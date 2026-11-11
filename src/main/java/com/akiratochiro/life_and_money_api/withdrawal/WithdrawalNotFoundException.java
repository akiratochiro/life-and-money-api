package com.akiratochiro.life_and_money_api.withdrawal;

public class WithdrawalNotFoundException extends RuntimeException {
    public WithdrawalNotFoundException() {
        super("Retirada não encontrada.");
    }
}
