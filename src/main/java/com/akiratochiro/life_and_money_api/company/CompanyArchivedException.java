package com.akiratochiro.life_and_money_api.company;

public class CompanyArchivedException extends RuntimeException {
    public CompanyArchivedException() {
        super("Esta empresa está arquivada. Desarquive-a para fazer alterações.");
    }
}
