package com.akiratochiro.life_and_money_api.invoice;

import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    /** A receita das notas ativas no intervalo, para as fases seguintes (impostos, visão geral). */
    @Transactional(readOnly = true)
    public List<RevenueSeries.Entry> entries(Long companyId, LocalDate from, LocalDate to) {
        return repository.findActiveRevenue(companyId, from, to);
    }
}
