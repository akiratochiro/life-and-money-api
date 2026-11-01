package com.akiratochiro.life_and_money_api.bill;

public class SavingCategoryBillException extends RuntimeException {
    public SavingCategoryBillException() {
        super("Contas a pagar e a receber usam categorias de receita ou de despesa.");
    }
}
