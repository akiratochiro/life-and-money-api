package com.akiratochiro.life_and_money_api.user;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
