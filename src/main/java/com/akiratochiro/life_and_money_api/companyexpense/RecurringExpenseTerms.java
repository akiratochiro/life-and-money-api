package com.akiratochiro.life_and_money_api.companyexpense;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O que pode mudar numa despesa fixa (nome, categoria, valor previsto, dia), com vigência, como os
 * termos das contas pessoais: um mês passado continua com o valor da época.
 */
@Entity
@Table(name = "company_recurring_expense_terms")
public class RecurringExpenseTerms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long recurringExpenseId;
    private Long categoryId;
    private String name;
    private BigDecimal amount;
    private int dueDay;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;

    protected RecurringExpenseTerms() {
    }

    RecurringExpenseTerms(Long userId, Long recurringExpenseId, Long categoryId, String name, BigDecimal amount,
                          int dueDay, YearMonth validFrom) {
        this.userId = userId;
        this.recurringExpenseId = recurringExpenseId;
        this.validFrom = validFrom.atDay(1);
        this.createdAt = Instant.now();
        replace(categoryId, name, amount, dueDay);
    }

    public Long getId() { return id; }
    public Long getRecurringExpenseId() { return recurringExpenseId; }
    public Long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public int getDueDay() { return dueDay; }
    public YearMonth getValidFrom() { return YearMonth.from(validFrom); }
    public YearMonth getValidTo() { return validTo == null ? null : YearMonth.from(validTo); }
    public boolean isActive() { return validTo == null; }

    boolean isValidIn(YearMonth month) {
        return !month.isBefore(getValidFrom()) && (validTo == null || !month.isAfter(getValidTo()));
    }

    /** O dia de vencimento no mês; um dia que o mês não tem cai no último (31 em fevereiro vira 28 ou 29). */
    LocalDate dueDateIn(YearMonth month) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    /** Só para os termos que começaram no mês corrente: ainda não há passado para preservar. */
    void replace(Long categoryId, String name, BigDecimal amount, int dueDay) {
        this.categoryId = categoryId;
        this.name = name;
        this.amount = amount;
        this.dueDay = dueDay;
    }

    void close(YearMonth lastMonth) {
        this.validTo = lastMonth.atDay(1);
    }
}
