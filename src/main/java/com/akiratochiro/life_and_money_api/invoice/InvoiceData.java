package com.akiratochiro.life_and_money_api.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os campos de uma nota, nos dois sentidos: o que a pré-visualização reconheceu (pode vir
 * incompleto, do PDF) e o que o usuário confirma ao salvar um PDF ou uma nota manual. Numa nota do
 * XML, o servidor ignora estes campos e relê o arquivo.
 */
public record InvoiceData(
        @NotBlank(message = "Informe o número da nota.")
        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
        String number,

        @Schema(description = "Os 50 dígitos da chave de acesso, sem espaços")
        @Pattern(regexp = "[0-9]{50}", message = "A chave de acesso tem 50 dígitos.")
        String accessKey,

        @NotNull(message = "Informe a data de emissão.")
        LocalDate issueDate,

        @Schema(description = "É por ela que a receita conta")
        @NotNull(message = "Informe a competência.")
        LocalDate competenceDate,

        @NotNull(message = "Informe o valor do serviço.")
        @Positive(message = "O valor do serviço precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal serviceAmount,

        @Schema(description = "Desconto incondicional: não é receita")
        @PositiveOrZero(message = "O desconto não pode ser negativo.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal unconditionalDiscountAmount,

        @NotNull(message = "Informe o valor líquido.")
        @PositiveOrZero(message = "O valor líquido não pode ser negativo.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal netAmount,

        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal issWithheldAmount,
        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal irrfWithheldAmount,
        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal pisWithheldAmount,
        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal cofinsWithheldAmount,
        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal csllWithheldAmount,
        @PositiveOrZero(message = "A retenção não pode ser negativa.") @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal inssWithheldAmount,

        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.")
        String takerName,

        @Schema(description = "CNPJ, CPF ou NIF (estrangeiro)")
        @Size(max = 40, message = "O documento deve ter no máximo 40 caracteres.")
        String takerDocument
) {
    InvoiceFields toFields() {
        return new InvoiceFields(number, accessKey, issueDate, competenceDate, serviceAmount,
                unconditionalDiscountAmount, netAmount, issWithheldAmount, irrfWithheldAmount, pisWithheldAmount,
                cofinsWithheldAmount, csllWithheldAmount, inssWithheldAmount, takerName, takerDocument);
    }

    static InvoiceData from(InvoiceFields f) {
        return new InvoiceData(f.number(), f.accessKey(), f.issueDate(), f.competenceDate(), f.serviceAmount(),
                f.unconditionalDiscountAmount(), f.netAmount(), f.issWithheldAmount(), f.irrfWithheldAmount(),
                f.pisWithheldAmount(), f.cofinsWithheldAmount(), f.csllWithheldAmount(), f.inssWithheldAmount(),
                f.takerName(), f.takerDocument());
    }
}
