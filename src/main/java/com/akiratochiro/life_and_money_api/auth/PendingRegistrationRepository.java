package com.akiratochiro.life_and_money_api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByEmail(String email);

    @Query("select p from PendingRegistration p where p.tokenHash = :tokenHash and p.expiresAt > :moment")
    Optional<PendingRegistration> findValid(@Param("tokenHash") String tokenHash, @Param("moment") Instant moment);

    /**
     * Apaga o cadastro pendente, devolvendo 1 se apagou e 0 se outra requisição já o tinha usado.
     * É o que garante o uso único do link: dois cliques ao mesmo tempo leem a mesma linha, mas só
     * um consegue apagá-la.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PendingRegistration p where p.id = :id")
    int consume(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PendingRegistration p where p.expiresAt <= :moment")
    int deleteExpired(@Param("moment") Instant moment);
}
