package com.akiratochiro.life_and_money_api.company;

public class PartnerNotFoundException extends RuntimeException {
    public PartnerNotFoundException() {
        super("Sócio não encontrado.");
    }
}
