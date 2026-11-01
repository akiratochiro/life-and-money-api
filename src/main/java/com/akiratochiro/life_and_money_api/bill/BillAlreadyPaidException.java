package com.akiratochiro.life_and_money_api.bill;

public class BillAlreadyPaidException extends RuntimeException {
    public BillAlreadyPaidException() {
        super("Este mês já está marcado como pago.");
    }
}
