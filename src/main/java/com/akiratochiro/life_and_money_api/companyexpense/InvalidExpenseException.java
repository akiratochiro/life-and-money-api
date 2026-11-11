package com.akiratochiro.life_and_money_api.companyexpense;

/** Uma despesa que a regra recusa. {@code conflict} separa o 409 (já pago) do 422 (regra). */
public class InvalidExpenseException extends RuntimeException {

    private final boolean conflict;

    InvalidExpenseException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    static InvalidExpenseException rule(String message) {
        return new InvalidExpenseException(message, false);
    }

    static InvalidExpenseException alreadyPaid() {
        return new InvalidExpenseException("Este mês já está pago.", true);
    }

    public boolean isConflict() {
        return conflict;
    }
}
