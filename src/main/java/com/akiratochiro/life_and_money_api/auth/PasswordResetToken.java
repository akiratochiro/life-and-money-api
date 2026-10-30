package com.akiratochiro.life_and_money_api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Um pedido de redefinição de senha. Vale por pouco tempo e uma vez só; quem decide se ainda vale
 * é o {@code consume} do repositório, numa única instrução no banco.
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** SHA-256 em hexadecimal: 64 caracteres, sempre. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;

    protected PasswordResetToken() {
    }

    PasswordResetToken(Long userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    Long getUserId() {
        return userId;
    }
}
