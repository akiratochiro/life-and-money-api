package com.akiratochiro.life_and_money_api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emite, rotaciona e revoga os refresh tokens.
 *
 * <p>O token é um valor opaco de 256 bits, não um JWT: ele precisa ser revogável, e um JWT
 * autocontido só seria revogável com uma lista no servidor — isto é, com a tabela que já existe
 * aqui. Então o valor aleatório é mais simples e não promete o que não cumpre.
 *
 * <p>Cada uso rotaciona: o token apresentado é revogado e um novo é emitido. Se um token já
 * revogado reaparece, só há duas explicações — cópia vazada ou corrida de rede — e nas duas o
 * mais seguro é derrubar todas as sessões do usuário e pedir login de novo.
 */
@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final Duration lifetime;

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                        Clock clock,
                        @Value("${jwt.refresh-expiration}") Duration lifetime) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    Duration lifetime() {
        return lifetime;
    }

    /** Emite um token novo para o usuário e devolve o valor em claro, que só o navegador verá. */
    @Transactional
    String issue(Long userId) {
        String value = OpaqueTokens.generate();

        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(userId, OpaqueTokens.hash(value), now, now.plus(lifetime)));
        return value;
    }

    /**
     * Troca o token apresentado por um novo. Devolve o usuário da sessão e o próximo token.
     *
     * <p>O {@code noRollbackFor} é essencial aqui, não um detalhe: ao detectar reuso, o método
     * revoga as sessões do usuário e <em>em seguida</em> lança a exceção. Com o comportamento
     * padrão do Spring, a exceção marcaria a transação para rollback e desfaria justamente a
     * revogação que acabou de ser feita. Nos outros caminhos de falha nada foi alterado, então
     * confirmar a transação não grava nada.
     *
     * @throws InvalidRefreshTokenException se o token é desconhecido, expirado ou já usado
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    Rotation rotate(String presentedValue) {
        if (presentedValue == null || presentedValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(presentedValue))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = clock.instant();

        if (stored.isRevoked()) {
            log.warn("Refresh token já usado foi apresentado de novo (usuário {}): "
                    + "revogando todas as sessões dele", stored.getUserId());
            refreshTokenRepository.revokeAllOfUser(stored.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (stored.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        stored.revoke(now);
        return new Rotation(stored.getUserId(), issue(stored.getUserId()));
    }

    /**
     * Revoga o token apresentado, se existir. Um logout com token desconhecido ou já expirado
     * não é erro: o resultado pedido — não ter mais sessão — já está valendo.
     */
    @Transactional
    void revoke(String presentedValue) {
        if (presentedValue == null || presentedValue.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(presentedValue))
                .ifPresent(stored -> stored.revoke(clock.instant()));
    }

    /** Encerra todas as sessões do usuário, como depois de uma troca de senha. */
    @Transactional
    void revokeAllOf(Long userId) {
        refreshTokenRepository.revokeAllOfUser(userId, clock.instant());
    }

    record Rotation(Long userId, String nextToken) {
    }
}
