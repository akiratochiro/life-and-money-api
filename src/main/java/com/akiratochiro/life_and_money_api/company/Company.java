package com.akiratochiro.life_and_money_api.company;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A empresa: a identidade que não muda com o enquadramento. O enquadramento tem vigência e mora em
 * {@link CompanyRegime}; aqui ficam só os dados cadastrais e o ponto de partida do saldo.
 */
@Entity
@Table(name = "companies")
public class Company {

    static final BigDecimal DEFAULT_FATOR_R_TARGET = new BigDecimal("0.28");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String name;
    private String emoji;
    /** {@code CHAR(14)} no banco: o CNPJ tem sempre 14 caracteres, e o Hibernate validaria como VARCHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 14)
    private String cnpj;
    private LocalDate openingDate;

    @Enumerated(EnumType.STRING)
    private RevenueBasis revenueBasis;

    @Column(name = "fator_r_target")
    private BigDecimal fatorRTarget;

    private BigDecimal openingBalance;
    private LocalDate openingBalanceDate;
    private boolean archived;
    private Instant archivedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Company() {
    }

    Company(Long userId, String name, String emoji, Cnpj cnpj, LocalDate openingDate,
            BigDecimal fatorRTarget, BigDecimal openingBalance, LocalDate openingBalanceDate) {
        this.userId = userId;
        this.name = name;
        this.emoji = emoji;
        this.cnpj = cnpj == null ? null : cnpj.value();
        this.openingDate = openingDate;
        this.revenueBasis = RevenueBasis.COMPETENCIA;
        this.fatorRTarget = fatorRTarget;
        this.openingBalance = openingBalance;
        this.openingBalanceDate = openingBalanceDate;
        this.archived = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    /** Sem pontuação; {@code null} quando o usuário não informou. */
    public String getCnpj() {
        return cnpj;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public RevenueBasis getRevenueBasis() {
        return revenueBasis;
    }

    public BigDecimal getFatorRTarget() {
        return fatorRTarget;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public LocalDate getOpeningBalanceDate() {
        return openingBalanceDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void rename(String newName) {
        this.name = newName;
        touch();
    }

    void changeEmoji(String newEmoji) {
        this.emoji = newEmoji;
        touch();
    }

    void changeCnpj(Cnpj newCnpj) {
        this.cnpj = newCnpj == null ? null : newCnpj.value();
        touch();
    }

    void changeFatorRTarget(BigDecimal target) {
        this.fatorRTarget = target;
        touch();
    }

    void changeOpeningBalance(BigDecimal balance, LocalDate date) {
        this.openingBalance = balance;
        this.openingBalanceDate = date;
        touch();
    }

    /** Quem chama confere antes se a data ainda pode mudar (ver {@code CompanyService}). */
    void changeOpeningDate(LocalDate date) {
        this.openingDate = date;
        touch();
    }

    void archive(Instant now) {
        this.archived = true;
        this.archivedAt = now;
        touch();
    }

    void unarchive() {
        this.archived = false;
        this.archivedAt = null;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
