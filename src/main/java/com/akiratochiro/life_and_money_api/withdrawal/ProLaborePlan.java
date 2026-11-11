package com.akiratochiro.life_and_money_api.withdrawal;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/** O pró-labore planejado de um sócio, com vigência como o orçamento: mudar abre uma vigência nova. */
@Entity
@Table(name = "partner_pro_labore_plans")
public class ProLaborePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long partnerId;
    private BigDecimal grossAmount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;

    protected ProLaborePlan() {
    }

    ProLaborePlan(Long userId, Long partnerId, BigDecimal grossAmount, YearMonth validFrom) {
        this.userId = userId;
        this.partnerId = partnerId;
        this.grossAmount = grossAmount;
        this.validFrom = validFrom.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getPartnerId() { return partnerId; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public YearMonth getValidFrom() { return YearMonth.from(validFrom); }
    public YearMonth getValidTo() { return validTo == null ? null : YearMonth.from(validTo); }

    boolean isValidIn(YearMonth month) {
        return !month.isBefore(getValidFrom()) && (validTo == null || !month.isAfter(getValidTo()));
    }

    void close(YearMonth lastMonth) {
        this.validTo = lastMonth.atDay(1);
    }

    void replace(BigDecimal amount) {
        this.grossAmount = amount;
    }
}
