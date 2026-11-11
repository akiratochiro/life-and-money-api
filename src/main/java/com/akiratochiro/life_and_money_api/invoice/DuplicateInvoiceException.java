package com.akiratochiro.life_and_money_api.invoice;

public class DuplicateInvoiceException extends RuntimeException {
    public DuplicateInvoiceException(String message) {
        super(message);
    }
}
