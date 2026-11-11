package com.akiratochiro.life_and_money_api.tax;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Uma obrigação: do sistema (sem período; o enquadramento de cada mês decide se vale) ou
 * personalizada (com período e, opcionalmente, valor fixo).
 */
@Entity
@Table(name = "tax_obligations")
public class TaxObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;

    @Enumerated(EnumType.STRING)
    private ObligationKind kind;

    private String name;

    @Enumerated(EnumType.STRING)
    private Recurrence recurrence;

    private int dueDay;
    private Integer dueMonth;

    @Enumerated(EnumType.STRING)
    private DueRule dueRule;

    private BigDecimal fixedAmount;
    private boolean system;
    private boolean active;
    private LocalDate startMonth;
    private LocalDate endMonth;
    private Instant createdAt;

    protected TaxObligation() {
    }

    TaxObligation(Long userId, Long companyId, ObligationKind kind, String name, Recurrence recurrence, int dueDay,
                  Integer dueMonth, DueRule dueRule, BigDecimal fixedAmount, YearMonth startMonth, YearMonth endMonth) {
        this.userId = userId;
        this.companyId = companyId;
        this.kind = kind;
        this.system = kind != ObligationKind.CUSTOM;
        this.active = true;
        this.createdAt = Instant.now();
        change(name, recurrence, dueDay, dueMonth, dueRule, fixedAmount, startMonth, endMonth);
    }

    void change(String name, Recurrence recurrence, int dueDay, Integer dueMonth, DueRule dueRule, BigDecimal fixedAmount,
                YearMonth startMonth, YearMonth endMonth) {
        this.name = name;
        this.recurrence = recurrence;
        this.dueDay = dueDay;
        this.dueMonth = dueMonth;
        this.dueRule = dueRule;
        this.fixedAmount = fixedAmount;
        this.startMonth = startMonth == null ? null : startMonth.atDay(1);
        this.endMonth = endMonth == null ? null : endMonth.atDay(1);
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public ObligationKind getKind() { return kind; }
    public String getName() { return name; }
    public Recurrence getRecurrence() { return recurrence; }
    public int getDueDay() { return dueDay; }
    public Integer getDueMonth() { return dueMonth; }
    public DueRule getDueRule() { return dueRule; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
    public boolean isSystem() { return system; }
    public boolean isActive() { return active; }
    public YearMonth getStartMonth() { return startMonth == null ? null : YearMonth.from(startMonth); }
    public YearMonth getEndMonth() { return endMonth == null ? null : YearMonth.from(endMonth); }
}
