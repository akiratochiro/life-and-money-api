package com.akiratochiro.life_and_money_api.user;

/**
 * Senha errada numa ação de quem já está logado (como excluir a conta). Não é um 401: para o
 * front, 401 significa sessão encerrada, e errar a senha não pode deslogar ninguém.
 */
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("Senha incorreta.");
    }
}
