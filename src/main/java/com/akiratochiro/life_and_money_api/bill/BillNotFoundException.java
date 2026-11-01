package com.akiratochiro.life_and_money_api.bill;

public class BillNotFoundException extends RuntimeException {
    public BillNotFoundException() {
        super("Conta não encontrada.");
    }
}
