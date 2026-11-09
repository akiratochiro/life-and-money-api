package com.akiratochiro.life_and_money_api.auth;

/**
 * Diz se uma senha já apareceu em vazamentos de dados conhecidos. Interface para os testes
 * trocarem a consulta de rede por uma lista fixa.
 */
public interface BreachedPasswords {

    /** {@code false} também quando não foi possível consultar: ver {@link PwnedPasswordsClient}. */
    boolean isBreached(String password);
}
