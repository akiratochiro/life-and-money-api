package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * "Esqueci minha senha": emite o token que vai por e-mail e, com ele, troca a senha.
 */
@Service
class PasswordResetService {

    static final Duration VALIDITY = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokens;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    PasswordResetService(PasswordResetTokenRepository tokens,
                         UserService userService,
                         RefreshTokenService refreshTokenService,
                         ApplicationEventPublisher eventPublisher,
                         Clock clock) {
        this.tokens = tokens;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Registra o pedido e agenda o e-mail. Um e-mail sem conta não faz nada, e quem chama não
     * fica sabendo: a resposta da API é a mesma nos dois casos, para ela não servir de consulta
     * de quem tem cadastro.
     *
     * <p>O e-mail sai depois do commit, em outra thread (ver {@link PasswordResetEmailListener}).
     * Se saísse aqui, a chamada ao provedor deixaria a resposta mais lenta só para quem tem conta,
     * e o tempo de resposta revelaria o que o corpo esconde.
     */
    @Transactional
    void requestReset(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            Instant now = clock.instant();
            tokens.revokeActiveOfUser(user.getId(), now);

            String value = OpaqueTokens.generate();
            tokens.save(new PasswordResetToken(user.getId(), OpaqueTokens.hash(value), now, now.plus(VALIDITY)));

            eventPublisher.publishEvent(
                    new PasswordResetRequested(user.getId(), user.getEmail(), user.getName(), value));
        });
    }

    /**
     * Troca a senha e derruba todas as sessões do usuário: se a senha foi trocada porque alguém
     * a descobriu, esse alguém não pode continuar logado.
     *
     * @throws InvalidPasswordResetTokenException se o token não existe, já foi usado, foi
     *         invalidado por um pedido mais novo ou expirou — sem dizer qual dos quatro
     */
    @Transactional
    void resetPassword(String token, String newPassword) {
        String tokenHash = OpaqueTokens.hash(token);
        Instant now = clock.instant();

        if (tokens.consume(tokenHash, now) == 0) {
            throw new InvalidPasswordResetTokenException();
        }

        Long userId = tokens.findByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordResetTokenException::new)
                .getUserId();

        userService.changePassword(userId, newPassword);
        refreshTokenService.revokeAllOf(userId);
        tokens.revokeActiveOfUser(userId, now);
    }
}
