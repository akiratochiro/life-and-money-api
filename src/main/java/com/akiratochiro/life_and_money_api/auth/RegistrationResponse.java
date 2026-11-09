package com.akiratochiro.life_and_money_api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Sempre a mesma mensagem, tenha ou não uma conta com o e-mail informado. */
public record RegistrationResponse(@Schema(requiredMode = REQUIRED) String message) {

    static final RegistrationResponse INSTANCE = new RegistrationResponse(
            "Enviamos um link para o seu e-mail. Abra-o em até "
                    + RegistrationService.VALIDITY.toHours() + " horas para ativar a conta.");
}
