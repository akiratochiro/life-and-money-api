package com.akiratochiro.life_and_money_api.bill;

public class BillOccurrenceNotFoundException extends RuntimeException {
    public BillOccurrenceNotFoundException() {
        super("Esta conta não vence nesse mês.");
    }
}
