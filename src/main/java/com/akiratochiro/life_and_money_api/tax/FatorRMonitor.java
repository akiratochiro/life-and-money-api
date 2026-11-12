package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.AnnexConfig;
import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.ExpectedRevenueService;
import com.akiratochiro.life_and_money_api.company.Partner;
import com.akiratochiro.life_and_money_api.company.PartnerService;
import com.akiratochiro.life_and_money_api.company.TaxRegime;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.taxengine.FatorRProjection;
import com.akiratochiro.life_and_money_api.taxengine.TaxCalculationException;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.withdrawal.ProLaboreSource;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Junta os dados do monitor do Fator R (decisão 30) e chama {@link FatorRProjection}. Usa a mesma
 * receita, folha e janela do DAS ({@link ExpectedTaxCalculator}), para o monitor e a aba Impostos nunca
 * divergirem quando o mês fechar.
 */
@Service
public class FatorRMonitor {

    static final String NOTICE = "O Fator R é projetado com a receita e a folha previstas do mês. "
            + "Estimativa do app: confirme com o seu contador.";
    static final String NOT_CALCULATED = "O app ainda não calcula o Fator R de 2027: as regras novas entram antes de fevereiro.";

    private final CompanyService companyService;
    private final PartnerService partnerService;
    private final ExpectedRevenueService expectedRevenueService;
    private final RevenueService revenueService;
    private final CompanyExpenseService expenseService;
    private final WithdrawalService withdrawalService;
    private final ExpectedTaxCalculator calculator;
    private final Clock clock;

    public FatorRMonitor(CompanyService companyService, PartnerService partnerService, ExpectedRevenueService expectedRevenueService,
                         RevenueService revenueService, CompanyExpenseService expenseService, WithdrawalService withdrawalService,
                         ExpectedTaxCalculator calculator, Clock clock) {
        this.companyService = companyService;
        this.partnerService = partnerService;
        this.expectedRevenueService = expectedRevenueService;
        this.revenueService = revenueService;
        this.expenseService = expenseService;
        this.withdrawalService = withdrawalService;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FatorRMonitorResponse monitor(Long userId, Long companyId) {
        Company company = companyService.getOwned(userId, companyId);
        YearMonth current = YearMonth.now(clock);
        YearMonth next = current.plusMonths(1);

        Optional<CompanyRegime> regime = companyService.regimeIn(companyId, next);
        if (regime.isEmpty() || regime.get().getTaxRegime() != TaxRegime.SIMPLES_NACIONAL
                || regime.get().getAnnexConfig() != AnnexConfig.FATOR_R) {
            return new FatorRMonitorResponse(false, null, null, null, null, null, null, null, null, null, null, NOTICE);
        }

        // O mês corrente projetado: max(notas, faturamento esperado), e o pró-labore registrado ou planejado.
        YearMonth start = YearMonth.from(company.getOpeningDate());
        YearMonth from = start.isAfter(next.minusMonths(12)) ? start : next.minusMonths(12);
        Map<YearMonth, BigDecimal> revenue = new HashMap<>();
        revenueService.monthly(companyId, from, current).forEach((month, value) -> revenue.put(month, value.total()));
        BigDecimal invoices = revenue.getOrDefault(current, BigDecimal.ZERO);
        Optional<BigDecimal> expected = expectedRevenueService.amountIn(companyId, current);
        BigDecimal projectedRevenue = expected.filter(e -> e.compareTo(invoices) > 0).orElse(invoices);
        revenue.put(current, projectedRevenue);

        List<ProLaboreSource> proLabores = withdrawalService.proLabores(companyId, current, true);
        BigDecimal proLabore = proLabores.stream().map(ProLaboreSource::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherPayroll = expenseService.projectedPayroll(userId, companyId, current);
        Map<YearMonth, BigDecimal> payroll = new HashMap<>(calculator.payroll(companyId, from, current));
        payroll.put(current, proLabore.add(otherPayroll));

        FatorRMonitorResponse.FatorRMonth month = new FatorRMonitorResponse.FatorRMonth(invoices, expected.orElse(null),
                projectedRevenue, proLabore, otherPayroll);

        Optional<TaxTable> table = calculator.table(next);
        if (table.isEmpty()) {
            return unavailable(next, current, month, NOT_CALCULATED);
        }

        Optional<Partner> me = partnerService.listOf(companyId).stream().filter(Partner::isMe).findFirst();
        Optional<ProLaboreSource> mine = me.flatMap(partner -> proLabores.stream()
                .filter(source -> source.partnerId().equals(partner.getId())).findFirst());
        BigDecimal myProLabore = mine.map(ProLaboreSource::gross).orElse(BigDecimal.ZERO);

        try {
            FatorRProjection.Result result = FatorRProjection.project(next, start, payroll, revenue, company.getFatorRTarget(),
                    myProLabore, projectedRevenue, table.get());
            FatorRProjection.Suggestion s = result.suggestion();
            FatorRMonitorResponse.FatorRSuggestion suggestion = s == null ? null : new FatorRMonitorResponse.FatorRSuggestion(
                    s.missingPayroll(), me.map(Partner::getId).orElse(null), s.currentProLabore(), s.suggestedProLabore(),
                    mine.map(ProLaboreSource::registered).orElse(false), s.savings(), s.dasAnnexIII(), s.dasAnnexV(),
                    s.extraProLaboreTaxes());
            var window = result.fatorR().window();
            return new FatorRMonitorResponse(true, next, current, month,
                    new FatorRMonitorResponse.FatorRWindow(window.from(), window.to(), result.fatorR().revenue(), result.fatorR().payroll()),
                    result.fatorR().ratio(), result.target(), result.threshold(), result.status(), suggestion, null, NOTICE);
        } catch (TaxCalculationException e) {
            return unavailable(next, current, month, e.getMessage());
        }
    }

    private static FatorRMonitorResponse unavailable(YearMonth next, YearMonth current, FatorRMonitorResponse.FatorRMonth month,
                                                     String reason) {
        return new FatorRMonitorResponse(true, next, current, month, null, null, null, null, null, null, reason, NOTICE);
    }
}
