package com.akiratochiro.life_and_money_api.withdrawal;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O que saiu da conta da empresa para um sócio. No pró-labore, guarda o INSS e o IRRF como foram
 * retidos: uma tabela nova não reescreve o que já foi pago.
 */
@Entity
@Table(name = "withdrawals")
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    private WithdrawalKind kind;

    private LocalDate competenceMonth;
    private LocalDate paymentDate;
    private BigDecimal grossAmount;
    private BigDecimal inssAmount;
    private BigDecimal irrfAmount;
    private BigDecimal netAmount;
    private Long personalTransactionId;
    private Instant createdAt;
    private Instant updatedAt;

    protected Withdrawal() {
    }

    Withdrawal(Long userId, Long companyId, Long partnerId, WithdrawalKind kind, YearMonth competence, LocalDate paymentDate,
               BigDecimal gross, BigDecimal inss, BigDecimal irrf) {
        this.userId = userId;
        this.companyId = companyId;
        this.partnerId = partnerId;
        this.kind = kind;
        this.competenceMonth = competence == null ? null : competence.atDay(1);
        this.createdAt = Instant.now();
        change(paymentDate, gross, inss, irrf);
    }

    void change(LocalDate paymentDate, BigDecimal gross, BigDecimal inss, BigDecimal irrf) {
        this.paymentDate = paymentDate;
        this.grossAmount = gross;
        this.inssAmount = inss;
        this.irrfAmount = irrf;
        this.netAmount = gross.subtract(inss).subtract(irrf);
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getPartnerId() { return partnerId; }
    public WithdrawalKind getKind() { return kind; }
    public YearMonth getCompetenceMonth() { return competenceMonth == null ? null : YearMonth.from(competenceMonth); }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getInssAmount() { return inssAmount; }
    public BigDecimal getIrrfAmount() { return irrfAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public Long getPersonalTransactionId() { return personalTransactionId; }
    public Long getUserId() { return userId; }

    /** Lançada nas finanças pessoais (Fase 6): a transação de receita com o líquido. */
    void linkTo(Long transactionId) {
        this.personalTransactionId = transactionId;
        this.updatedAt = Instant.now();
    }

    void unlink() {
        this.personalTransactionId = null;
        this.updatedAt = Instant.now();
    }
}
