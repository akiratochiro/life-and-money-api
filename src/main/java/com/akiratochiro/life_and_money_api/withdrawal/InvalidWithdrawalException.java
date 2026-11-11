package com.akiratochiro.life_and_money_api.withdrawal;

/** Uma retirada que a regra recusa. {@code conflict} separa o 409 (já existe) do 422 (regra). */
public class InvalidWithdrawalException extends RuntimeException {

    private final boolean conflict;

    InvalidWithdrawalException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
