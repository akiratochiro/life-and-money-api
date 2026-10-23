package com.akiratochiro.life_and_money_api.user;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
        super("Este e-mail já está em uso.");
    }
}
