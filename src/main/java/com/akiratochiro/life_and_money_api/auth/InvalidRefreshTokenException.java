package com.akiratochiro.life_and_money_api.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Sua sessão expirou. Entre de novo.");
    }
}
