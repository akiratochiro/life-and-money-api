package com.akiratochiro.life_and_money_api.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * O limite do BCrypt é em bytes, não em caracteres. Ver {@link ValidPassword}.
 */
class PasswordBytesValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Nulo e vazio ficam com o @NotBlank, que dá a mensagem certa.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
