package com.akiratochiro.life_and_money_api.company;

/** A consulta de CNPJ não teve resposta utilizável. Nunca chega ao usuário como erro: vira {@code available=false}. */
public class CnpjLookupException extends RuntimeException {
    public CnpjLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
