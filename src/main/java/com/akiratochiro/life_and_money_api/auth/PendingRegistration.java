package com.akiratochiro.life_and_money_api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Um cadastro esperando a confirmação do e-mail. Ver a migration V12. */
@Entity
@Table(name = "pending_registrations")
class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String name;
    private String passwordHash;

    /** SHA-256 em hexadecimal: 64 caracteres, sempre. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    private Instant createdAt;
    private Instant expiresAt;

    protected PendingRegistration() {
    }

    PendingRegistration(String email) {
        this.email = email;
    }

    /** Um pedido novo para o mesmo e-mail troca tudo, inclusive o link: o anterior para de valer. */
    void renew(String name, String passwordHash, String tokenHash, Instant now, Instant expiresAt) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean wasCreatedAfter(Instant moment) {
        return createdAt != null && createdAt.isAfter(moment);
    }

    Long getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getName() {
        return name;
    }

    String getPasswordHash() {
        return passwordHash;
    }
}
