package com.akiratochiro.life_and_money_api.bill;

public class BillPaymentNotFoundException extends RuntimeException {
    public BillPaymentNotFoundException() {
        super("Este mês não está marcado como pago.");
    }
}
