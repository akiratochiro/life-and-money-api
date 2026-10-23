package com.akiratochiro.life_and_money_api.transaction;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException() {
        super("Transação não encontrada.");
    }
}
