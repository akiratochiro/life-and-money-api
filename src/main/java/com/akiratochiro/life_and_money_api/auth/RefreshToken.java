package com.akiratochiro.life_and_money_api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Uma sessão de longa duração. Guarda o hash do token, nunca o token em si: quem tem acesso ao
 * banco não consegue se passar por um usuário, e a comparação continua possível porque o hash
 * do valor apresentado é determinístico.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** SHA-256 em hexadecimal: 64 caracteres, sempre. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;

    protected RefreshToken() {
    }

    RefreshToken(Long userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    void revoke(Instant moment) {
        if (revokedAt == null) {
            this.revokedAt = moment;
        }
    }
}
