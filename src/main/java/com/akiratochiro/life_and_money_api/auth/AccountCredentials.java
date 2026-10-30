package com.akiratochiro.life_and_money_api.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A parte do {@code auth} que a exclusão da conta precisa: apagar as sessões e os pedidos de
 * redefinição de senha do usuário, e o cookie que limpa a sessão no navegador. É uma porta pública
 * pequena para os repositórios e o cookie continuarem fechados dentro do pacote.
 */
@Component
public class AccountCredentials {

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenCookie refreshTokenCookie;

    AccountCredentials(RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository passwordResetTokens,
                       RefreshTokenCookie refreshTokenCookie) {
        this.refreshTokens = refreshTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @Transactional
    public void deleteAllOf(Long userId) {
        refreshTokens.deleteAllByUserId(userId);
        passwordResetTokens.deleteAllByUserId(userId);
    }

    /** Valor do Set-Cookie que apaga o refresh token no navegador. */
    public String expiredSessionCookie() {
        return refreshTokenCookie.expired().toString();
    }
}
