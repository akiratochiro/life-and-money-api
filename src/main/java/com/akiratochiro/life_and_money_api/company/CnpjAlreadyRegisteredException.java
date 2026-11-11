package com.akiratochiro.life_and_money_api.company;

/** 409 com o campo marcado: o formulário mostra o erro no CNPJ. */
public class CnpjAlreadyRegisteredException extends RuntimeException {
    public CnpjAlreadyRegisteredException() {
        super("Você já cadastrou uma empresa com este CNPJ.");
    }
}
