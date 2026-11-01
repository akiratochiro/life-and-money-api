package com.akiratochiro.life_and_money_api.bill;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O fato de uma ocorrência ter sido paga (ou recebida). Não guarda valor nem data: os dois moram
 * só na transação ligada, então editar a transação no extrato corrige o pagamento também.
 */
@Entity
@Table(name = "bill_payments")
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long billId;
    private LocalDate dueMonth;
    private Long transactionId;
    private Instant createdAt;

    protected BillPayment() {
    }

    public BillPayment(Long userId, Long billId, YearMonth dueMonth, Long transactionId) {
        this.userId = userId;
        this.billId = billId;
        this.dueMonth = dueMonth.atDay(1);
        this.transactionId = transactionId;
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

    public YearMonth getDueMonth() {
        return YearMonth.from(dueMonth);
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
