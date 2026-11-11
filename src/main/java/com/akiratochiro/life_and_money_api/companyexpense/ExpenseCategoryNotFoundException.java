package com.akiratochiro.life_and_money_api.companyexpense;

public class ExpenseCategoryNotFoundException extends RuntimeException {
    public ExpenseCategoryNotFoundException() {
        super("Categoria não encontrada.");
    }
}
