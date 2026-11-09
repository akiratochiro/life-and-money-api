package com.akiratochiro.life_and_money_api.auth;

public class InvalidConfirmationTokenException extends RuntimeException {

    InvalidConfirmationTokenException() {
        super("Este link de confirmação é inválido ou expirou. Faça o cadastro de novo para receber outro.");
    }
}
