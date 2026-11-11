package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Os campos lidos do XML de uma nota, como a pré-visualização os mostra. */
public record InvoiceData(
        @Schema(requiredMode = REQUIRED) String number,
        @Schema(requiredMode = REQUIRED, description = "Os 50 dígitos da chave de acesso") String accessKey,
        @Schema(requiredMode = REQUIRED) LocalDate issueDate,
        @Schema(requiredMode = REQUIRED, description = "É por ela que a receita conta") LocalDate competenceDate,
        @Schema(requiredMode = REQUIRED) BigDecimal serviceAmount,
        @Schema(requiredMode = REQUIRED, description = "Desconto incondicional: não é receita") BigDecimal unconditionalDiscountAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal netAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal issWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal irrfWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal pisWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal cofinsWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal csllWithheldAmount,
        @Schema(requiredMode = REQUIRED) BigDecimal inssWithheldAmount,
        @Schema(description = "O tomador é opcional na NFS-e") String takerName,
        @Schema(description = "CNPJ, CPF ou NIF (estrangeiro)") String takerDocument
) {
    static InvoiceData from(InvoiceFields f) {
        return new InvoiceData(f.number(), f.accessKey(), f.issueDate(), f.competenceDate(), f.serviceAmount(),
                f.unconditionalDiscountAmount(), f.netAmount(), f.issWithheldAmount(), f.irrfWithheldAmount(),
                f.pisWithheldAmount(), f.cofinsWithheldAmount(), f.csllWithheldAmount(), f.inssWithheldAmount(),
                f.takerName(), f.takerDocument());
    }
}
