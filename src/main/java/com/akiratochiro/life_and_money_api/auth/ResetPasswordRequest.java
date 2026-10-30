package com.akiratochiro.life_and_money_api.auth;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "O link para criar uma senha nova está incompleto.")
        String token,

        @ValidPassword
        String newPassword
) {
}
