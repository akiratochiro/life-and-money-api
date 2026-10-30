package com.akiratochiro.life_and_money_api.auth;

/** Sempre a mesma mensagem, tenha ou não uma conta com o e-mail informado. */
public record ForgotPasswordResponse(String message) {

    static final ForgotPasswordResponse INSTANCE = new ForgotPasswordResponse(
            "Se existir uma conta com esse e-mail, enviamos um link para criar uma senha nova. "
                    + "Ele vale por " + PasswordResetService.VALIDITY.toMinutes() + " minutos.");
}
