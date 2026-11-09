package com.akiratochiro.life_and_money_api.auth;

/** A senha escolhida já apareceu num vazamento. Diz em que campo ela veio, para o front marcá-lo. */
public class BreachedPasswordException extends RuntimeException {

    private final String field;

    BreachedPasswordException(String field) {
        super("Essa senha já apareceu em vazamentos de dados e é das primeiras que um invasor tenta. "
                + "Escolha outra.");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
