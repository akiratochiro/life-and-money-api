package com.akiratochiro.life_and_money_api.companyexpense;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/** A identidade de uma despesa fixa: de que mês até que mês ela existe. O resto mora nos termos. */
@Entity
@Table(name = "company_recurring_expenses")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private LocalDate startMonth;
    private LocalDate endMonth;
    private Instant createdAt;

    protected RecurringExpense() {
    }

    RecurringExpense(Long userId, Long companyId, YearMonth startMonth, YearMonth endMonth) {
        this.userId = userId;
        this.companyId = companyId;
        this.startMonth = startMonth.atDay(1);
        this.endMonth = endMonth == null ? null : endMonth.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public YearMonth getStartMonth() { return YearMonth.from(startMonth); }
    public YearMonth getEndMonth() { return endMonth == null ? null : YearMonth.from(endMonth); }

    boolean occursIn(YearMonth month) {
        return !month.isBefore(getStartMonth()) && (endMonth == null || !month.isAfter(getEndMonth()));
    }

    void endAt(YearMonth month) {
        this.endMonth = month == null ? null : month.atDay(1);
    }
}
