package com.akiratochiro.life_and_money_api.user;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Usuário não encontrado.");
    }
}
