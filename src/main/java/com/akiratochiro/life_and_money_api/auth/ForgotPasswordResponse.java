package com.akiratochiro.life_and_money_api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Sempre a mesma mensagem, tenha ou não uma conta com o e-mail informado. */
public record ForgotPasswordResponse(@Schema(requiredMode = REQUIRED) String message) {

    static final ForgotPasswordResponse INSTANCE = new ForgotPasswordResponse(
            "Se existir uma conta com esse e-mail, enviamos um link para criar uma senha nova. "
                    + "Ele vale por " + PasswordResetService.VALIDITY.toMinutes() + " minutos.");
}
