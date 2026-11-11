package com.akiratochiro.life_and_money_api.tax;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O pagamento de uma ocorrência (obrigação + competência). O valor e a data pagos moram na despesa
 * de impostos; a declaração guarda só o dia da entrega. O previsto fica congelado aqui.
 */
@Entity
@Table(name = "tax_payments")
public class TaxPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long obligationId;
    private LocalDate competenceMonth;
    private BigDecimal expectedAmount;
    private Long expenseId;
    private LocalDate deliveredOn;
    private Instant createdAt;

    protected TaxPayment() {
    }

    TaxPayment(Long userId, Long obligationId, YearMonth competence, BigDecimal expectedAmount, Long expenseId,
               LocalDate deliveredOn) {
        this.userId = userId;
        this.obligationId = obligationId;
        this.competenceMonth = competence.atDay(1);
        this.expectedAmount = expectedAmount;
        this.expenseId = expenseId;
        this.deliveredOn = deliveredOn;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getObligationId() { return obligationId; }
    public YearMonth getCompetenceMonth() { return YearMonth.from(competenceMonth); }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public Long getExpenseId() { return expenseId; }
    public LocalDate getDeliveredOn() { return deliveredOn; }
}
