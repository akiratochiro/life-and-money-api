package com.akiratochiro.life_and_money_api.company;

/** Uma mudança de enquadramento que reescreveria o passado ou que não muda nada. */
public class InvalidRegimeChangeException extends RuntimeException {
    public InvalidRegimeChangeException(String message) {
        super(message);
    }
}
