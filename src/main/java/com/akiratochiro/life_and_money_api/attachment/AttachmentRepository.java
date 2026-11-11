package com.akiratochiro.life_and_money_api.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndUserId(Long id, Long userId);

    /** Só a soma dos tamanhos, sem carregar nenhum arquivo. */
    @Query("select coalesce(sum(a.sizeBytes), 0) from Attachment a where a.userId = :userId")
    long sumSizeByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Attachment a where a.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
