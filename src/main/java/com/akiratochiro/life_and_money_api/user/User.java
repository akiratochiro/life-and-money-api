package com.akiratochiro.life_and_money_api.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private Instant createdAt;

    protected User(){}

    public User(String name, String email, String passwordHash){
        this.name = name;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName(){
        return name;
    }

    public String getEmail(){
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Recebe o hash, nunca a senha: quem calcula o BCrypt é o UserService. */
    void changePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash is required");
    }

    public void changeEmail(String newEmail){
        this.email = normalizeEmail(newEmail);
    }

    static String normalizeEmail(String email){
        Objects.requireNonNull(email, "email is required");
        return email.toLowerCase(Locale.ROOT).strip();
    }
}