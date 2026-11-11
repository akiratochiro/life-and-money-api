package com.akiratochiro.life_and_money_api.tax;

/** Uma operação de imposto que a regra recusa. {@code conflict} separa o 409 (já pago) do 422 (regra). */
public class InvalidTaxOperationException extends RuntimeException {

    private final boolean conflict;

    InvalidTaxOperationException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
