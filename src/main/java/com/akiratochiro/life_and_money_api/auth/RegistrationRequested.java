package com.akiratochiro.life_and_money_api.auth;

/**
 * Um cadastro foi pedido e um e-mail precisa sair: o link de confirmação ({@code token} preenchido)
 * ou, quando o e-mail já tem conta, o aviso ao dono ({@code token} nulo).
 */
record RegistrationRequested(String email, String name, String token) {

    boolean accountAlreadyExists() {
        return token == null;
    }

    /** Sem o token e sem o e-mail: um log descuidado do evento não entrega nada. */
    @Override
    public String toString() {
        return "RegistrationRequested[accountAlreadyExists=" + accountAlreadyExists() + "]";
    }
}
