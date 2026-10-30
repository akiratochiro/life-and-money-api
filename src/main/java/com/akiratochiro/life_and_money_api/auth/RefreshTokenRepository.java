package com.akiratochiro.life_and_money_api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * O {@code flushAutomatically} não é opcional: o {@code clearAutomatically} limpa o contexto de
     * persistência depois do update, e qualquer alteração ainda não gravada na mesma transação —
     * como a senha nova, na redefinição — seria descartada sem erro nenhum.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :moment
             where t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeAllOfUser(@Param("userId") Long userId, @Param("moment") Instant moment);

    /**
     * Apaga tudo do usuário, para a exclusão da conta. O flush antes e o clear depois evitam que
     * alterações pendentes se percam ou que entidades já apagadas continuem no contexto.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
