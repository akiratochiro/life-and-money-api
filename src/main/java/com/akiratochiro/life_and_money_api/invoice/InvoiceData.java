package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Os campos lidos do XML de uma nota, como a pré-visualização os mostra. */
public record InvoiceData(
        String number,

        @Schema(description = "Os 50 dígitos da chave de acesso, sem espaços")
        String accessKey,

        LocalDate issueDate,

        @Schema(description = "É por ela que a receita conta")
        LocalDate competenceDate,

        BigDecimal serviceAmount,

        @Schema(description = "Desconto incondicional: não é receita")
        BigDecimal unconditionalDiscountAmount,

        BigDecimal netAmount,

        BigDecimal issWithheldAmount,
        BigDecimal irrfWithheldAmount,
        BigDecimal pisWithheldAmount,
        BigDecimal cofinsWithheldAmount,
        BigDecimal csllWithheldAmount,
        BigDecimal inssWithheldAmount,

        String takerName,

        @Schema(description = "CNPJ, CPF ou NIF (estrangeiro)")
        String takerDocument
) {
    static InvoiceData from(InvoiceFields f) {
        return new InvoiceData(f.number(), f.accessKey(), f.issueDate(), f.competenceDate(), f.serviceAmount(),
                f.unconditionalDiscountAmount(), f.netAmount(), f.issWithheldAmount(), f.irrfWithheldAmount(),
                f.pisWithheldAmount(), f.cofinsWithheldAmount(), f.csllWithheldAmount(), f.inssWithheldAmount(),
                f.takerName(), f.takerDocument());
    }
}
