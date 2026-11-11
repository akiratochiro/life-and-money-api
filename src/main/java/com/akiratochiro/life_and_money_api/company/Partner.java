package com.akiratochiro.life_and_money_api.company;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Sócio da empresa. A API aceita vários, porque a folha do Fator R soma os pró-labores de todos;
 * a tela, por enquanto, cadastra e mostra só o "sou eu".
 */
@Entity
@Table(name = "partners")
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private String name;
    private boolean isMe;
    private Instant createdAt;

    protected Partner() {
    }

    Partner(Long userId, Long companyId, String name, boolean isMe) {
        this.userId = userId;
        this.companyId = companyId;
        this.name = name;
        this.isMe = isMe;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public boolean isMe() {
        return isMe;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void rename(String newName) {
        this.name = newName;
    }
}
