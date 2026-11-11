package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record InvoiceResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) InvoiceSource source,
        @Schema(requiredMode = REQUIRED) InvoiceStatus status,
        @Schema(requiredMode = REQUIRED) String number,
        @Schema(description = "Ausente em nota sem chave (PDF ou manual)") String accessKey,
        @Schema(requiredMode = REQUIRED) LocalDate issueDate,
        @Schema(requiredMode = REQUIRED) LocalDate competenceDate,
        @Schema(requiredMode = REQUIRED) BigDecimal serviceAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal unconditionalDiscountAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal netAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal issWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal irrfWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal pisWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal cofinsWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal csllWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal inssWithheldAmount,
        @Schema(description = "O tomador pode não ter sido informado na nota") String takerName,
        String takerDocument,
        @Schema(description = "Chave da nota que esta substituiu") String replacedAccessKey
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(invoice.getId(), invoice.getSource(), invoice.getStatus(), invoice.getNumber(),
                invoice.getAccessKey(), invoice.getIssueDate(), invoice.getCompetenceDate(), invoice.getServiceAmount(),
                invoice.getUnconditionalDiscountAmount(), invoice.getNetAmount(), invoice.getIssWithheldAmount(),
                invoice.getIrrfWithheldAmount(), invoice.getPisWithheldAmount(), invoice.getCofinsWithheldAmount(),
                invoice.getCsllWithheldAmount(), invoice.getInssWithheldAmount(), invoice.getTakerName(),
                invoice.getTakerDocument(), invoice.getReplacedAccessKey());
    }
}
