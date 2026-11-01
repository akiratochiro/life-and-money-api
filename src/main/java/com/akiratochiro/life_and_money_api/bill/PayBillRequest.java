package com.akiratochiro.life_and_money_api.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Os dois campos são opcionais: sem eles, vale o valor previsto e a data de hoje. */
public record PayBillRequest(
        @Positive(message = "O valor pago deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        @Schema(description = "Valor efetivo; ausente para usar o previsto (a conta de luz raramente bate)")
        BigDecimal amount,

        @Schema(description = "Data do pagamento; ausente para hoje")
        LocalDate paidOn
) {
}
