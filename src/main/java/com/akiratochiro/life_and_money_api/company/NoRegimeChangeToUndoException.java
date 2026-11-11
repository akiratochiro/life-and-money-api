package com.akiratochiro.life_and_money_api.company;

public class NoRegimeChangeToUndoException extends RuntimeException {
    public NoRegimeChangeToUndoException() {
        super("Não há mudança de enquadramento para desfazer: este é o enquadramento desde a abertura.");
    }
}
