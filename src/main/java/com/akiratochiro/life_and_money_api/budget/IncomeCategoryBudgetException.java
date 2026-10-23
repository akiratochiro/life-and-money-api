package com.akiratochiro.life_and_money_api.budget;

public class IncomeCategoryBudgetException extends RuntimeException {
    public IncomeCategoryBudgetException() {
        super("Categorias de receita não entram no orçamento.");
    }
}
