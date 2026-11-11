package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A receita da empresa pelo regime de competência: as notas ativas, pela data de competência, com
 * o serviço menos o desconto incondicional, que não é receita bruta (LC 123, art. 3º, §1º).
 */
@Service
public class RevenueService {

    private final InvoiceRepository repository;
    private final CompanyService companyService;

    public RevenueService(InvoiceRepository repository, CompanyService companyService) {
        this.repository = repository;
        this.companyService = companyService;
    }

    @Transactional(readOnly = true)
    public RevenueResponse revenue(Long userId, Long companyId, LocalDate from, LocalDate to,
                                   RevenueGranularity granularity) {
        companyService.getOwned(userId, companyId);
        RevenueSeries.Range range = RevenueSeries.range(from, to, granularity);
        List<RevenueSeries.Entry> entries = entries(companyId, range.from(), range.to());
        List<RevenueSeries.Point> points = RevenueSeries.build(range, granularity, entries);
        BigDecimal total = points.stream().map(RevenueSeries.Point::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueResponse(granularity, range.from(), range.to(), total, points.stream()
                .map(p -> new RevenueResponse.Point(p.start(), p.end(), p.amount(), p.invoices()))
                .toList());
    }

    /** A receita de um mês: o total e a parte com ISS retido pelo tomador. */
    public record MonthRevenue(BigDecimal total, BigDecimal withIssWithheld) {
        public static final MonthRevenue ZERO = new MonthRevenue(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** A receita por mês de competência, de {@code from} a {@code to} inclusive, para o DAS e o RBT12. */
    @Transactional(readOnly = true)
    public Map<YearMonth, MonthRevenue> monthly(Long companyId, YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> totals = byMonth(repository.findActiveRevenue(companyId, from.atDay(1), to.atEndOfMonth()));
        Map<YearMonth, BigDecimal> retained = byMonth(
                repository.findActiveRevenueWithIssWithheld(companyId, from.atDay(1), to.atEndOfMonth()));
        Map<YearMonth, MonthRevenue> result = new HashMap<>();
        totals.forEach((month, total) -> result.put(month, new MonthRevenue(total, retained.getOrDefault(month, BigDecimal.ZERO))));
        return result;
    }

    private static Map<YearMonth, BigDecimal> byMonth(List<RevenueSeries.Entry> entries) {
        Map<YearMonth, BigDecimal> result = new HashMap<>();
        for (RevenueSeries.Entry entry : entries) {
            result.merge(YearMonth.from(entry.competenceDate()), entry.amount(), BigDecimal::add);
        }
        return result;
    }

    /** A receita das notas ativas no intervalo, para as fases seguintes (impostos, visão geral). */
    @Transactional(readOnly = true)
    public List<RevenueSeries.Entry> entries(Long companyId, LocalDate from, LocalDate to) {
        return repository.findActiveRevenue(companyId, from, to);
    }
}
