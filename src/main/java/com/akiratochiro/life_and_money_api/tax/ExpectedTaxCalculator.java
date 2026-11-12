package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.AnnexConfig;
import com.akiratochiro.life_and_money_api.company.MeiActivity;
import com.akiratochiro.life_and_money_api.company.Partner;
import com.akiratochiro.life_and_money_api.company.PartnerService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.taxengine.Annex;
import com.akiratochiro.life_and_money_api.taxengine.FatorR;
import com.akiratochiro.life_and_money_api.taxengine.ProLaboreCalculator;
import com.akiratochiro.life_and_money_api.taxengine.Rbt12;
import com.akiratochiro.life_and_money_api.taxengine.SimeiCalculator;
import com.akiratochiro.life_and_money_api.taxengine.SimplesCalculator;
import com.akiratochiro.life_and_money_api.taxengine.TaxCalculationException;
import com.akiratochiro.life_and_money_api.taxengine.TaxRules;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.taxparam.TaxParameterService;
import com.akiratochiro.life_and_money_api.withdrawal.ProLaboreSource;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Junta os dados da empresa (receita, folha, pró-labores) e chama o motor puro. É aqui que o
 * banco encontra a lei; as fórmulas estão no {@code taxengine}.
 */
@Component
class ExpectedTaxCalculator {

    static final String NOT_CALCULATED = "O app calcula impostos de 01/2026 a 12/2026. Para este mês, registre o valor pago.";

    private final RevenueService revenueService;
    private final WithdrawalService withdrawalService;
    private final CompanyExpenseService expenseService;
    private final PartnerService partnerService;
    private final TaxParameterService taxParameters;
    private final Clock clock;

    ExpectedTaxCalculator(RevenueService revenueService, WithdrawalService withdrawalService, CompanyExpenseService expenseService,
                          PartnerService partnerService, TaxParameterService taxParameters, Clock clock) {
        this.revenueService = revenueService;
        this.withdrawalService = withdrawalService;
        this.expenseService = expenseService;
        this.partnerService = partnerService;
        this.taxParameters = taxParameters;
        this.clock = clock;
    }

    /** O DAS da competência, pela receita do mês, o RBT12 e o Anexo (fixo ou pelo Fator R). */
    ExpectedTax das(Company company, CompanyRegime regime, YearMonth competence) {
        Optional<TaxTable> table = table(competence);
        if (table.isEmpty()) {
            return ExpectedTax.none(NOT_CALCULATED);
        }
        YearMonth start = YearMonth.from(company.getOpeningDate());
        YearMonth from = later(start, competence.minusMonths(12));
        Map<YearMonth, RevenueService.MonthRevenue> revenue = revenueService.monthly(company.getId(), from, competence);
        Map<YearMonth, BigDecimal> totals = revenue.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().total()));
        RevenueService.MonthRevenue month = revenue.getOrDefault(competence, RevenueService.MonthRevenue.ZERO);
        try {
            Rbt12.Result rbt12 = Rbt12.of(competence, start, totals);
            Annex annex = Annex.III;
            FatorR.Result fatorR = null;
            if (regime.getAnnexConfig() == AnnexConfig.FATOR_R) {
                fatorR = FatorR.of(competence, start, payroll(company.getId(), from, competence), totals, table.get());
                annex = fatorR.annex();
            }
            SimplesCalculator.Result das = SimplesCalculator.das(annex, rbt12.value(), month.total(), month.withIssWithheld(), table.get());
            TaxCalculation.Simples memory = new TaxCalculation.Simples(month.total(), month.withIssWithheld(), rbt12.value(),
                    rbt12.window().method().name(), rbt12.window().from(), rbt12.window().to(), annex,
                    fatorR == null ? null : fatorR.ratio(), fatorR == null ? null : fatorR.payroll(), das.bracket(),
                    das.nominalRate(), das.deduction(), das.effectiveRate(), das.issRate());
            return new ExpectedTax(das.amount(), null, new TaxCalculation(memory, null, null));
        } catch (TaxCalculationException e) {
            return ExpectedTax.none(e.getMessage());
        }
    }

    ExpectedTax dasMei(CompanyRegime regime, YearMonth competence) {
        Optional<TaxTable> table = table(competence);
        if (table.isEmpty()) {
            return ExpectedTax.none(NOT_CALCULATED);
        }
        SimeiCalculator.Result das = SimeiCalculator.das(activity(regime.getMeiActivity()), table.get());
        return new ExpectedTax(das.amount(), null, new TaxCalculation(null, new TaxCalculation.Simei(das.inss(), das.icms(), das.iss()), null));
    }

    /**
     * O DARF da competência: INSS e IRRF dos pró-labores. O registrado vale como foi retido; o
     * planejado só entra a partir do mês passado (a previsão), calculado com a tabela da competência.
     * Supõe o pró-labore pago no próprio mês (plano da Fase 4, item 1.4).
     */
    ExpectedTax darf(Company company, YearMonth competence) {
        boolean includePlanned = !competence.isBefore(YearMonth.now(clock).minusMonths(1));
        List<ProLaboreSource> sources = withdrawalService.proLabores(company.getId(), competence, includePlanned);
        if (sources.isEmpty()) {
            return ExpectedTax.none("Sem pró-labore registrado ou planejado neste mês.");
        }
        Optional<TaxTable> table = table(competence);
        boolean needsTable = sources.stream().anyMatch(source -> !source.registered());
        if (needsTable && table.isEmpty()) {
            return ExpectedTax.none(NOT_CALCULATED);
        }
        Map<Long, String> names = partnerService.listOf(company.getId()).stream()
                .collect(Collectors.toMap(Partner::getId, Partner::getName));
        BigDecimal total = BigDecimal.ZERO;
        List<TaxCalculation.ProLaboreLine> lines = new java.util.ArrayList<>();
        for (ProLaboreSource source : sources) {
            BigDecimal inss = source.inss();
            BigDecimal irrf = source.irrf();
            if (!source.registered()) {
                ProLaboreCalculator.Result result = ProLaboreCalculator.of(source.gross(), table.get());
                inss = result.inss();
                irrf = result.irrf();
            }
            total = total.add(inss).add(irrf);
            lines.add(new TaxCalculation.ProLaboreLine(source.partnerId(), names.getOrDefault(source.partnerId(), "Sócio"),
                    source.gross(), inss, irrf, source.registered()));
        }
        String note = sources.stream().anyMatch(s -> !s.registered())
                ? "Inclui o pró-labore planejado, ainda não registrado como pago." : null;
        return new ExpectedTax(total, note, new TaxCalculation(null, null, new TaxCalculation.Darf(lines)));
    }

    /** Há pró-labore (registrado, ou planejado na janela da previsão) que faça o DARF existir neste mês? */
    boolean hasProLabore(Long companyId, YearMonth competence) {
        boolean includePlanned = !competence.isBefore(YearMonth.now(clock).minusMonths(1));
        return !withdrawalService.proLabores(companyId, competence, includePlanned).isEmpty();
    }

    Map<YearMonth, BigDecimal> payroll(Long companyId, YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> payroll = new HashMap<>(withdrawalService.proLaborePayroll(companyId, from, to));
        expenseService.payrollByMonth(companyId, from, to).forEach((month, amount) -> payroll.merge(month, amount, BigDecimal::add));
        return payroll;
    }

    /** A tabela da competência, só nos meses que o app calcula (com parâmetros e antes de 2027). */
    Optional<TaxTable> table(YearMonth competence) {
        if (!competence.isBefore(TaxRules.FIRST_UNSUPPORTED)) {
            return Optional.empty();
        }
        return taxParameters.findTableFor(competence);
    }

    private static SimeiCalculator.Activity activity(MeiActivity activity) {
        return switch (activity) {
            case SERVICOS -> SimeiCalculator.Activity.SERVICES;
            case COMERCIO_INDUSTRIA -> SimeiCalculator.Activity.TRADE_OR_INDUSTRY;
            case AMBOS -> SimeiCalculator.Activity.BOTH;
        };
    }

    private static YearMonth later(YearMonth a, YearMonth b) {
        return a.isAfter(b) ? a : b;
    }
}
