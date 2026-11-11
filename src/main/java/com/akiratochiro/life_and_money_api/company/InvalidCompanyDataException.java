package com.akiratochiro.life_and_money_api.company;

/**
 * Um campo da empresa com valor que a regra não aceita. Vira um 400 com o campo marcado, como um
 * erro de validação, para o formulário apontar onde está o problema.
 */
public class InvalidCompanyDataException extends RuntimeException {

    private final String field;

    public InvalidCompanyDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
