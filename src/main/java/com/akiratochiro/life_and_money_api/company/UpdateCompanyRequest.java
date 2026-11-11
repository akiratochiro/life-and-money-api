package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edição parcial: o que não vier fica como está. O enquadramento muda por outro endpoint, com vigência. */
public record UpdateCompanyRequest(
        String name,

        String emoji,

        @Schema(description = "Texto vazio remove o CNPJ; ausente, fica como está")
        String cnpj,

        @Schema(description = "Só enquanto openingDateEditable; move junto o início da primeira vigência")
        LocalDate openingDate,

        @DecimalMin(value = "0", inclusive = false, message = "A meta do Fator R precisa ser maior que zero.")
        @DecimalMax(value = "1", message = "A meta do Fator R não pode passar de 100%.")
        @Digits(integer = 1, fraction = 4, message = "Use no máximo duas casas decimais no percentual.")
        BigDecimal fatorRTarget,

        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal openingBalance,

        LocalDate openingBalanceDate
) {
    CompanyChanges toChanges() {
        return new CompanyChanges(name, emoji, cnpj, openingDate, fatorRTarget, openingBalance, openingBalanceDate);
    }
}
