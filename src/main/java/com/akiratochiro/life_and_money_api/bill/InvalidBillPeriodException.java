package com.akiratochiro.life_and_money_api.bill;

public class InvalidBillPeriodException extends RuntimeException {
    public InvalidBillPeriodException() {
        super("O último mês não pode ser antes do primeiro.");
    }
}
