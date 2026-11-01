package com.akiratochiro.life_and_money_api.bill;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A identidade de uma conta prevista: de quem é, como se repete e entre quais meses. O que pode
 * mudar com o tempo (nome, valor, categoria, dia) fica em {@link BillTerms}, com vigência.
 */
@Entity
@Table(name = "scheduled_bills")
public class ScheduledBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private BillRecurrence recurrence;

    private LocalDate startMonth;
    private LocalDate endMonth;
    private Instant createdAt;

    protected ScheduledBill() {
    }

    /** Uma conta única existe só no mês de início, por isso o fim dela é o próprio início. */
    public ScheduledBill(Long userId, BillRecurrence recurrence, YearMonth startMonth, YearMonth endMonth) {
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new IllegalArgumentException("End month " + endMonth + " is before start month " + startMonth);
        }
        this.userId = userId;
        this.recurrence = recurrence;
        this.startMonth = startMonth.atDay(1);
        YearMonth end = recurrence == BillRecurrence.ONCE ? startMonth : endMonth;
        this.endMonth = end == null ? null : end.atDay(1);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BillRecurrence getRecurrence() {
        return recurrence;
    }

    public YearMonth getStartMonth() {
        return YearMonth.from(startMonth);
    }

    public YearMonth getEndMonth() {
        return endMonth == null ? null : YearMonth.from(endMonth);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Se a conta tem uma ocorrência no mês. */
    public boolean occursIn(YearMonth month) {
        YearMonth start = getStartMonth();
        YearMonth end = getEndMonth();
        if (month.isBefore(start) || (end != null && month.isAfter(end))) {
            return false;
        }
        return switch (recurrence) {
            case ONCE -> month.equals(start);
            case MONTHLY -> true;
            case YEARLY -> month.getMonth() == start.getMonth();
        };
    }

    /** Muda o último mês. Uma conta única não tem fim a mudar: ele é sempre o mês dela. */
    public void endAt(YearMonth lastMonth) {
        if (lastMonth != null && lastMonth.isBefore(getStartMonth())) {
            throw new IllegalArgumentException("Cannot end bill " + id + " before its start month " + getStartMonth());
        }
        if (recurrence == BillRecurrence.ONCE) {
            return;
        }
        this.endMonth = lastMonth == null ? null : lastMonth.atDay(1);
    }
}
