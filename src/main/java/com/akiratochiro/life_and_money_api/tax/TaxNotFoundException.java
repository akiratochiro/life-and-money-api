package com.akiratochiro.life_and_money_api.tax;

public class TaxNotFoundException extends RuntimeException {
    public TaxNotFoundException(String message) {
        super(message);
    }
}
