package com.akiratochiro.life_and_money_api.company;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException() {
        super("Empresa não encontrada.");
    }
}
