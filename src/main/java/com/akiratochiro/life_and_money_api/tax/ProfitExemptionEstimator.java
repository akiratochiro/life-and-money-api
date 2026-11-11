package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.MeiActivity;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.taxengine.ProfitDistribution;
import com.akiratochiro.life_and_money_api.taxengine.SimplesBracket;
import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.withdrawal.ProfitExemptionLimits;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monta, mês a mês, a receita, a presunção e o IRPJ do DAS para o limite de isenção do lucro
 * distribuído (decisão 28). No MEI, a presunção é pela atividade (8% para "ambos", o conservador) e não
 * há IRPJ a descontar.
 */
@Component
class ProfitExemptionEstimator implements ProfitExemptionLimits {

    private final CompanyService companyService;
    private final RevenueService revenueService;
    private final ExpectedTaxCalculator calculator;

    ProfitExemptionEstimator(CompanyService companyService, RevenueService revenueService, ExpectedTaxCalculator calculator) {
        this.companyService = companyService;
        this.revenueService = revenueService;
        this.calculator = calculator;
    }

    @Override
    public Optional<BigDecimal> limitUntil(Long userId, Long companyId, YearMonth month) {
        Company company = companyService.getOwned(userId, companyId);
        YearMonth opening = YearMonth.from(company.getOpeningDate());
        YearMonth january = YearMonth.of(month.getYear(), 1);
        YearMonth first = opening.isAfter(january) ? opening : january;
        if (month.isBefore(first)) {
            return Optional.empty();
        }
        Map<YearMonth, RevenueService.MonthRevenue> revenue = revenueService.monthly(companyId, first, month);
        List<ProfitDistribution.Month> months = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(month); m = m.plusMonths(1)) {
            Optional<CompanyRegime> regime = companyService.regimeIn(companyId, m);
            Optional<TaxTable> table = calculator.table(m);
            if (regime.isEmpty() || table.isEmpty()) {
                return Optional.empty();
            }
            BigDecimal monthRevenue = revenue.getOrDefault(m, RevenueService.MonthRevenue.ZERO).total();
            switch (regime.get().getTaxRegime()) {
                case SIMEI -> months.add(new ProfitDistribution.Month(monthRevenue,
                        table.get().get(regime.get().getMeiActivity() == MeiActivity.SERVICOS
                                ? TaxParameter.PRESUMED_PROFIT_SERVICES : TaxParameter.PRESUMED_PROFIT_TRADE),
                        BigDecimal.ZERO));
                case SIMPLES_NACIONAL -> {
                    TaxCalculation calculation = calculator.das(company, regime.get(), m).calculation();
                    if (calculation == null || calculation.simples() == null) {
                        return Optional.empty();
                    }
                    TaxCalculation.Simples das = calculation.simples();
                    BigDecimal irpjShare = table.get().brackets(das.annex()).stream()
                            .filter(b -> b.bracket() == das.bracket()).findFirst().map(SimplesBracket::irpjShare).orElseThrow();
                    months.add(new ProfitDistribution.Month(monthRevenue, table.get().get(TaxParameter.PRESUMED_PROFIT_SERVICES),
                            ProfitDistribution.irpjInDas(das.revenue(), das.effectiveRate(), irpjShare)));
                }
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(ProfitDistribution.exemptionLimit(months));
    }
}
