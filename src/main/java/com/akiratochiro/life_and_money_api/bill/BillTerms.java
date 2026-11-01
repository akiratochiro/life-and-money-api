package com.akiratochiro.life_and_money_api.bill;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Os termos de uma conta durante um período: nome, categoria, valor previsto e dia de vencimento.
 * Mudar um termo fecha o atual e abre outro, como no orçamento, para que meses passados continuem
 * mostrando o que estava previsto na época.
 */
@Entity
@Table(name = "scheduled_bill_terms")
public class BillTerms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long billId;
    private Long categoryId;
    private String name;
    private BigDecimal amount;
    private Integer dueDay;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;

    protected BillTerms() {
    }

    public BillTerms(Long userId, Long billId, Long categoryId, String name, BigDecimal amount,
                     int dueDay, YearMonth validFrom) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("Due day must be between 1 and 31, got " + dueDay);
        }
        this.userId = userId;
        this.billId = billId;
        this.categoryId = categoryId;
        this.name = name.strip();
        this.amount = amount;
        this.dueDay = dueDay;
        this.validFrom = validFrom.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getDueDay() {
        return dueDay;
    }

    public YearMonth getValidFrom() {
        return YearMonth.from(validFrom);
    }

    public YearMonth getValidTo() {
        return validTo == null ? null : YearMonth.from(validTo);
    }

    public boolean isActive() {
        return validTo == null;
    }

    public boolean isValidIn(YearMonth month) {
        YearMonth to = getValidTo();
        return !month.isBefore(getValidFrom()) && (to == null || !month.isAfter(to));
    }

    /**
     * O vencimento no mês. Um dia que o mês não tem cai no último dia dele: dia 31 vence em 30/04
     * e em 28/02 (29/02 nos anos bissextos), em vez de pular para o mês seguinte.
     */
    public LocalDate dueDateIn(YearMonth month) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    /** Altera os termos sem guardar histórico: para quando não há mês passado a preservar. */
    public void replace(Long categoryId, String name, BigDecimal amount, int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("Due day must be between 1 and 31, got " + dueDay);
        }
        this.categoryId = categoryId;
        this.name = name.strip();
        this.amount = amount;
        this.dueDay = dueDay;
    }

    public void close(YearMonth lastMonth) {
        if (!isActive()) {
            throw new IllegalStateException("Bill terms " + id + " are already closed");
        }
        if (lastMonth.isBefore(getValidFrom())) {
            throw new IllegalArgumentException(
                    "Cannot close bill terms before their start month " + getValidFrom());
        }
        this.validTo = lastMonth.atDay(1);
    }
}
