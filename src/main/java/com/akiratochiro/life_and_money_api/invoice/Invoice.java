package com.akiratochiro.life_and_money_api.invoice;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Uma NFS-e de serviço prestado pela empresa. O arquivo original fica em {@code attachments}. */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private Long attachmentId;

    @Enumerated(EnumType.STRING)
    private InvoiceSource source;

    private String number;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 50)
    private String accessKey;

    private LocalDate issueDate;
    private LocalDate competenceDate;
    private BigDecimal serviceAmount;
    private BigDecimal unconditionalDiscountAmount;
    private BigDecimal netAmount;
    private BigDecimal issWithheldAmount;
    private BigDecimal irrfWithheldAmount;
    private BigDecimal pisWithheldAmount;
    private BigDecimal cofinsWithheldAmount;
    private BigDecimal csllWithheldAmount;
    private BigDecimal inssWithheldAmount;
    private String takerName;
    private String takerDocument;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 50)
    private String replacedAccessKey;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    protected Invoice() {
    }

    Invoice(Long userId, Long companyId, Long attachmentId, InvoiceSource source, InvoiceFields fields,
            String replacedAccessKey) {
        this.userId = userId;
        this.companyId = companyId;
        this.attachmentId = attachmentId;
        this.source = source;
        this.replacedAccessKey = replacedAccessKey;
        this.status = InvoiceStatus.ATIVA;
        this.createdAt = Instant.now();
        apply(fields);
    }

    void cancel() {
        status = InvoiceStatus.CANCELADA;
        updatedAt = Instant.now();
    }

    void reactivate() {
        status = InvoiceStatus.ATIVA;
        updatedAt = Instant.now();
    }

    private void apply(InvoiceFields fields) {
        this.number = fields.number().strip();
        this.accessKey = fields.accessKey();
        this.issueDate = fields.issueDate();
        this.competenceDate = fields.competenceDate();
        this.serviceAmount = fields.serviceAmount();
        this.unconditionalDiscountAmount = orZero(fields.unconditionalDiscountAmount());
        this.netAmount = fields.netAmount();
        this.issWithheldAmount = orZero(fields.issWithheldAmount());
        this.irrfWithheldAmount = orZero(fields.irrfWithheldAmount());
        this.pisWithheldAmount = orZero(fields.pisWithheldAmount());
        this.cofinsWithheldAmount = orZero(fields.cofinsWithheldAmount());
        this.csllWithheldAmount = orZero(fields.csllWithheldAmount());
        this.inssWithheldAmount = orZero(fields.inssWithheldAmount());
        this.takerName = blankToNull(fields.takerName());
        this.takerDocument = blankToNull(fields.takerDocument());
        this.updatedAt = Instant.now();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getAttachmentId() { return attachmentId; }
    public InvoiceSource getSource() { return source; }
    public String getNumber() { return number; }
    public String getAccessKey() { return accessKey; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getCompetenceDate() { return competenceDate; }
    public BigDecimal getServiceAmount() { return serviceAmount; }
    public BigDecimal getUnconditionalDiscountAmount() { return unconditionalDiscountAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getIssWithheldAmount() { return issWithheldAmount; }
    public BigDecimal getIrrfWithheldAmount() { return irrfWithheldAmount; }
    public BigDecimal getPisWithheldAmount() { return pisWithheldAmount; }
    public BigDecimal getCofinsWithheldAmount() { return cofinsWithheldAmount; }
    public BigDecimal getCsllWithheldAmount() { return csllWithheldAmount; }
    public BigDecimal getInssWithheldAmount() { return inssWithheldAmount; }
    public String getTakerName() { return takerName; }
    public String getTakerDocument() { return takerDocument; }
    public String getReplacedAccessKey() { return replacedAccessKey; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
