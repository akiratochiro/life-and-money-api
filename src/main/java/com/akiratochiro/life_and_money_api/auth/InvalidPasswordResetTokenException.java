package com.akiratochiro.life_and_money_api.auth;

/**
 * Uma mensagem só para link inexistente, usado, invalidado ou expirado: distinguir os casos não
 * ajuda quem perdeu o link (a saída é sempre pedir outro) e ajudaria quem está testando tokens.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("Este link para criar uma senha nova é inválido ou expirou. Peça outro.");
    }
}
