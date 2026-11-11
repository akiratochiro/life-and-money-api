package com.akiratochiro.life_and_money_api.invoice;

public class InvalidRevenueRangeException extends RuntimeException {
    public InvalidRevenueRangeException(String message) {
        super(message);
    }
}
