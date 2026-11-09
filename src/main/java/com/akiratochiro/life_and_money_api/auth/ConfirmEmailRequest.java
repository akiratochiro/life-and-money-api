package com.akiratochiro.life_and_money_api.auth;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailRequest(
        @NotBlank(message = "O link de confirmação está incompleto.")
        String token
) {
}
