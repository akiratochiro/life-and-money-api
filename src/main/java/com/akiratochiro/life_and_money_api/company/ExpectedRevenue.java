package com.akiratochiro.life_and_money_api.company;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O faturamento esperado do mês de uma empresa (Fase 8, decisão 30), com vigência como o pró-labore
 * planejado: mudar abre uma vigência nova a partir do mês corrente.
 */
@Entity
@Table(name = "company_expected_revenues")
public class ExpectedRevenue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;

    protected ExpectedRevenue() {
    }

    ExpectedRevenue(Long userId, Long companyId, BigDecimal amount, YearMonth validFrom) {
        this.userId = userId;
        this.companyId = companyId;
        this.amount = amount;
        this.validFrom = validFrom.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getCompanyId() { return companyId; }
    public BigDecimal getAmount() { return amount; }
    public YearMonth getValidFrom() { return YearMonth.from(validFrom); }
    public YearMonth getValidTo() { return validTo == null ? null : YearMonth.from(validTo); }

    boolean isValidIn(YearMonth month) {
        return !month.isBefore(getValidFrom()) && (validTo == null || !month.isAfter(getValidTo()));
    }

    void close(YearMonth lastMonth) {
        this.validTo = lastMonth.atDay(1);
    }

    void replace(BigDecimal amount) {
        this.amount = amount;
    }
}
