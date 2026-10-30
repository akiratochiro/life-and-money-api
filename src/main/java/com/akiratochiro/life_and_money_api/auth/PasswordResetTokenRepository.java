package com.akiratochiro.life_and_money_api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant moment);

    /**
     * Marca o token como usado se, e só se, ele ainda vale. Devolve 1 quando consumiu e 0 quando
     * o token não existe, já foi usado, foi invalidado ou expirou.
     *
     * <p>Conferir e marcar na mesma instrução é o que garante o uso único. Ler o token, checar no
     * Java e depois gravar deixaria uma janela: dois cliques simultâneos no mesmo link passariam
     * os dois pela checagem antes de algum gravar. Aqui o banco serializa as duas atualizações, e
     * a segunda já encontra {@code used_at} preenchido.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :moment
             where t.tokenHash = :tokenHash
               and t.usedAt is null
               and t.revokedAt is null
               and t.expiresAt > :moment
            """)
    int consume(@Param("tokenHash") String tokenHash, @Param("moment") Instant moment);

    /** Invalida os tokens ainda não usados do usuário: só o pedido mais recente vale. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.revokedAt = :moment
             where t.userId = :userId
               and t.usedAt is null
               and t.revokedAt is null
            """)
    int revokeActiveOfUser(@Param("userId") Long userId, @Param("moment") Instant moment);
}
