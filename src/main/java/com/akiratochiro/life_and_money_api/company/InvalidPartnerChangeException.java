package com.akiratochiro.life_and_money_api.company;

/** Uma mudança nos sócios que a regra não permite (sócio num MEI, remover a si mesmo). */
public class InvalidPartnerChangeException extends RuntimeException {
    public InvalidPartnerChangeException(String message) {
        super(message);
    }
}
