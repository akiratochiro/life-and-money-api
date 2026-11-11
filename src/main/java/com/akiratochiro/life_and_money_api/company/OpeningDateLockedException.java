package com.akiratochiro.life_and_money_api.company;

public class OpeningDateLockedException extends RuntimeException {
    public OpeningDateLockedException() {
        super("A data de abertura não pode mais ser alterada: a empresa já tem mudança de "
                + "enquadramento ou notas fiscais.");
    }
}
