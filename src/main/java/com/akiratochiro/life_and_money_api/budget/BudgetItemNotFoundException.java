package com.akiratochiro.life_and_money_api.budget;

public class BudgetItemNotFoundException extends RuntimeException {
    public BudgetItemNotFoundException() {
        super("Esta categoria não tem orçamento em vigor.");
    }
}
