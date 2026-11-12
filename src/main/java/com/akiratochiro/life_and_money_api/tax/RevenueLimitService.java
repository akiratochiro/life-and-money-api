package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyRegime;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.company.ExpectedRevenueService;
import com.akiratochiro.life_and_money_api.company.TaxRegime;
import com.akiratochiro.life_and_money_api.invoice.InvalidRevenueRangeException;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.taxengine.RevenueLimits;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import com.akiratochiro.life_and_money_api.taxparam.TaxParameterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Os limites de faturamento do ano (Fase 10, decisão 32): junta a receita, o enquadramento e a projeção,
 * chama o {@link RevenueLimits} puro e monta as frases.
 */
@Service
public class RevenueLimitService {

    private final CompanyService companyService;
    private final RevenueService revenueService;
    private final ExpectedRevenueService expectedRevenueService;
    private final TaxParameterService taxParameters;
    private final Clock clock;

    public RevenueLimitService(CompanyService companyService, RevenueService revenueService, ExpectedRevenueService expectedRevenueService,
                               TaxParameterService taxParameters, Clock clock) {
        this.companyService = companyService;
        this.revenueService = revenueService;
        this.expectedRevenueService = expectedRevenueService;
        this.taxParameters = taxParameters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevenueLimitsResponse limits(Long userId, Long companyId, Integer requestedYear) {
        Company company = companyService.getOwned(userId, companyId);
        YearMonth current = YearMonth.now(clock);
        YearMonth start = YearMonth.from(company.getOpeningDate());
        int first = Math.max(start.getYear(), TaxParameterService.FIRST_SUPPORTED.getYear());
        int last = current.getYear();
        int year = requestedYear != null ? requestedYear : last;
        if (year < first || year > last) {
            throw new InvalidRevenueRangeException(first == last
                    ? "Os limites de faturamento estão disponíveis só para " + last + "."
                    : "Os limites de faturamento vão de " + first + " a " + last + ".");
        }
        List<Integer> years = new ArrayList<>();
        for (int y = last; y >= first; y--) {
            years.add(y);
        }

        // O enquadramento vigente hoje; num ano passado, o de dezembro daquele ano.
        Optional<CompanyRegime> regime = companyService.regimeIn(companyId, year == last ? current : YearMonth.of(year, 12));
        RevenueLimits.Regime kind = regime.map(r -> r.getTaxRegime() == TaxRegime.SIMEI ? RevenueLimits.Regime.MEI
                : r.getTaxRegime() == TaxRegime.SIMPLES_NACIONAL ? RevenueLimits.Regime.SIMPLES : null).orElse(null);
        if (kind == null) {
            return new RevenueLimitsResponse(false, year, years, regime.map(CompanyRegime::getTaxRegime).orElse(null), false, 0,
                    BigDecimal.ZERO, null, List.of(), null, null, RevenueLimitMessages.NOTICE);
        }

        YearMonth january = YearMonth.of(year, 1);
        Map<YearMonth, BigDecimal> actual = new HashMap<>();
        revenueService.monthly(companyId, january, YearMonth.of(year, 12)).forEach((month, value) -> actual.put(month, value.total()));
        TaxTable table = taxParameters.tableFor(january);

        YearMonth projectedFrom = null;
        Map<YearMonth, BigDecimal> projected = new HashMap<>();
        RevenueLimitsResponse.Basis basis = null;
        if (year == last) {
            projectedFrom = current;
            Optional<BigDecimal> expected = expectedRevenueService.amountIn(companyId, current);
            BigDecimal invoices = actual.getOrDefault(current, BigDecimal.ZERO);
            BigDecimal perMonth;
            if (expected.isPresent()) {
                basis = RevenueLimitsResponse.Basis.EXPECTED_REVENUE;
                perMonth = expected.get();
                projected.put(current, invoices.max(perMonth));
            } else {
                basis = RevenueLimitsResponse.Basis.AVERAGE;
                perMonth = average(actual, start.isAfter(january) ? start : january, current, invoices);
                projected.put(current, invoices);
            }
            for (YearMonth m = current.plusMonths(1); m.getYear() == year; m = m.plusMonths(1)) {
                if (!m.isBefore(start)) {
                    projected.put(m, perMonth);
                }
            }
        }

        RevenueLimits.Result result = RevenueLimits.of(year, kind, start, actual, projectedFrom, projected, table);
        List<RevenueLimitsResponse.LimitView> limits = result.limits().stream()
                .map(limit -> new RevenueLimitsResponse.LimitView(limit.kind(), RevenueLimitMessages.label(limit.kind()), limit.amount(),
                        limit.toleranceAmount(), limit.ratio(), limit.level(), RevenueLimitMessages.severity(limit), limit.exceededIn(),
                        limit.effectiveFrom(), limit.projectedIn(), RevenueLimitMessages.message(limit, year, result.revenue(), start),
                        RevenueLimitMessages.projection(limit), RevenueLimitMessages.source(limit.kind())))
                .toList();
        return new RevenueLimitsResponse(true, year, years, regime.get().getTaxRegime(), result.openingYear(), result.activityMonths(),
                result.revenue(), result.projectionTotal() == null ? null : new RevenueLimitsResponse.Projection(result.projectionTotal(), basis),
                limits, result.alert(), kind == RevenueLimits.Regime.MEI && year >= 2027 ? RevenueLimitMessages.CONDITIONAL : null,
                RevenueLimitMessages.NOTICE);
    }

    /** A média dos meses de atividade antes do corrente; sem nenhum, as notas do próprio mês. */
    private static BigDecimal average(Map<YearMonth, BigDecimal> actual, YearMonth from, YearMonth current, BigDecimal invoices) {
        BigDecimal total = BigDecimal.ZERO;
        int months = 0;
        for (YearMonth m = from; m.isBefore(current); m = m.plusMonths(1)) {
            total = total.add(actual.getOrDefault(m, BigDecimal.ZERO));
            months++;
        }
        return months == 0 ? invoices : total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
