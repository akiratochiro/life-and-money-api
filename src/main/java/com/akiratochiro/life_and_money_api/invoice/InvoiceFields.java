package com.akiratochiro.life_and_money_api.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Os campos de uma nota, como lidos do XML. */
record InvoiceFields(
        String number,
        String accessKey,
        LocalDate issueDate,
        LocalDate competenceDate,
        BigDecimal serviceAmount,
        BigDecimal unconditionalDiscountAmount,
        BigDecimal netAmount,
        BigDecimal issWithheldAmount,
        BigDecimal irrfWithheldAmount,
        BigDecimal pisWithheldAmount,
        BigDecimal cofinsWithheldAmount,
        BigDecimal csllWithheldAmount,
        BigDecimal inssWithheldAmount,
        String takerName,
        String takerDocument
) {
    static InvoiceFields from(ParsedInvoice parsed) {
        return new InvoiceFields(parsed.number(), parsed.accessKey(), parsed.issueDate(), parsed.competenceDate(),
                parsed.serviceAmount(), parsed.unconditionalDiscountAmount(), parsed.netAmount(),
                parsed.issWithheldAmount(), parsed.irrfWithheldAmount(), parsed.pisWithheldAmount(),
                parsed.cofinsWithheldAmount(), parsed.csllWithheldAmount(), parsed.inssWithheldAmount(),
                parsed.takerName(), parsed.takerDocument());
    }
}
