package com.akiratochiro.life_and_money_api;

import com.akiratochiro.life_and_money_api.auth.BreachedPasswords;

import java.util.Set;

/**
 * Uma lista fixa no lugar da Pwned Passwords: nenhum teste depende da rede. As senhas que os
 * outros testes usam ("senha12345", "senhaNova987") ficam de fora de propósito.
 */
public class KnownBreachedPasswords implements BreachedPasswords {

    public static final String BREACHED = "123456789";

    private static final Set<String> LIST = Set.of(BREACHED, "password123", "qwerty123");

    @Override
    public boolean isBreached(String password) {
        return LIST.contains(password);
    }
}
