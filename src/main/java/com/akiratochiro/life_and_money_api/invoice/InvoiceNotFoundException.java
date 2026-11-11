package com.akiratochiro.life_and_money_api.invoice;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException() {
        super("Nota fiscal não encontrada.");
    }
}
