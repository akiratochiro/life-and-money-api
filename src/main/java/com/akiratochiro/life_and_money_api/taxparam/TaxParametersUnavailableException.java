package com.akiratochiro.life_and_money_api.taxparam;

/** Não há parâmetros para a data pedida: o app calcula impostos a partir de janeiro de 2026. */
public class TaxParametersUnavailableException extends RuntimeException {
    public TaxParametersUnavailableException(String message) {
        super(message);
    }
}
