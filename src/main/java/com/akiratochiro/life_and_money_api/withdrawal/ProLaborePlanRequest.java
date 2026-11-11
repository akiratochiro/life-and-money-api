package com.akiratochiro.life_and_money_api.withdrawal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProLaborePlanRequest(
        @NotNull(message = "Informe o pró-labore bruto planejado.")
        @Positive(message = "O valor precisa ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal grossAmount
) {
}
