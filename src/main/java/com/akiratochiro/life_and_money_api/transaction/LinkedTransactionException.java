package com.akiratochiro.life_and_money_api.transaction;

/** Mudar valor ou data de uma transação que veio de outra feature: é por lá que se muda (422). */
public class LinkedTransactionException extends RuntimeException {
    public LinkedTransactionException(String message) {
        super(message);
    }
}
