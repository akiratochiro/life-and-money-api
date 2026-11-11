package com.akiratochiro.life_and_money_api.companyexpense;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(String message) {
        super(message);
    }

    static ExpenseNotFoundException expense() {
        return new ExpenseNotFoundException("Despesa não encontrada.");
    }

    static ExpenseNotFoundException recurring() {
        return new ExpenseNotFoundException("Despesa fixa não encontrada.");
    }

    static ExpenseNotFoundException occurrence() {
        return new ExpenseNotFoundException("Esta despesa fixa não tem vencimento neste mês.");
    }

    static ExpenseNotFoundException receipt() {
        return new ExpenseNotFoundException("Esta despesa não tem comprovante.");
    }
}
