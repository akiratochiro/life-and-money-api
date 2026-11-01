package com.akiratochiro.life_and_money_api.bill;

public class BillEndedException extends RuntimeException {
    public BillEndedException() {
        super("Esta conta já foi encerrada. Para mudar os valores, reabra a conta tirando o último mês.");
    }
}
