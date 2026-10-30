package com.akiratochiro.life_and_money_api.auth;

/**
 * Um pedido de redefinição foi registrado e o link precisa sair por e-mail. Carrega o token em
 * claro, que existe só em memória até virar o link.
 */
record PasswordResetRequested(Long userId, String email, String name, String token) {

    /** Sem o token: um log descuidado do evento não pode entregar um link válido. */
    @Override
    public String toString() {
        return "PasswordResetRequested[userId=" + userId + "]";
    }
}
