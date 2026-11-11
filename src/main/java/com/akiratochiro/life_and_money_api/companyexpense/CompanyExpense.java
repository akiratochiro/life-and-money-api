package com.akiratochiro.life_and_money_api.companyexpense;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Uma despesa paga: avulsa, ou o pagamento de um mês de uma fixa. O nome e a categoria da fixa são
 * copiados no pagamento, para o mês pago continuar igual se a fixa mudar depois.
 */
@Entity
@Table(name = "company_expenses")
public class CompanyExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private Long categoryId;
    private String name;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private Long attachmentId;
    private Long recurringExpenseId;
    private LocalDate occurrenceMonth;
    private Instant createdAt;
    private Instant updatedAt;

    protected CompanyExpense() {
    }

    CompanyExpense(Long userId, Long companyId, Long categoryId, String name, BigDecimal amount, LocalDate expenseDate,
                   Long recurringExpenseId, YearMonth occurrenceMonth) {
        this.userId = userId;
        this.companyId = companyId;
        this.categoryId = categoryId;
        this.name = name;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.recurringExpenseId = recurringExpenseId;
        this.occurrenceMonth = occurrenceMonth == null ? null : occurrenceMonth.atDay(1);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public Long getAttachmentId() { return attachmentId; }
    public Long getRecurringExpenseId() { return recurringExpenseId; }
    public YearMonth getOccurrenceMonth() { return occurrenceMonth == null ? null : YearMonth.from(occurrenceMonth); }
    public boolean isRecurringPayment() { return recurringExpenseId != null; }

    void change(Long categoryId, String name, BigDecimal amount, LocalDate expenseDate) {
        this.categoryId = categoryId;
        this.name = name;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.updatedAt = Instant.now();
    }

    void attach(Long attachmentId) {
        this.attachmentId = attachmentId;
        this.updatedAt = Instant.now();
    }
}
