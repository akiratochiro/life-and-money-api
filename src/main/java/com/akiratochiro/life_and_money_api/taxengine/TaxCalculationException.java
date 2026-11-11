package com.akiratochiro.life_and_money_api.taxengine;

/**
 * Um caso que o app não calcula, com a explicação para o usuário: regras de 2027 ainda não
 * implementadas, faturamento acima do sublimite, acima do teto do Simples.
 */
public class TaxCalculationException extends RuntimeException {
    public TaxCalculationException(String message) {
        super(message);
    }
}
