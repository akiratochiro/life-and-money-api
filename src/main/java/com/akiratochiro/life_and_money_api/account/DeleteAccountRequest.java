package com.akiratochiro.life_and_money_api.account;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Informe sua senha.")
        String password
) {
}
