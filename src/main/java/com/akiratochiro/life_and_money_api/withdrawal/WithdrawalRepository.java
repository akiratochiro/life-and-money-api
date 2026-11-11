package com.akiratochiro.life_and_money_api.withdrawal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    Optional<Withdrawal> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Withdrawal> findByPersonalTransactionId(Long transactionId);

    List<Withdrawal> findAllByUserIdAndPersonalTransactionIdIn(Long userId, java.util.Collection<Long> transactionIds);

    List<Withdrawal> findAllByCompanyIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(Long companyId, LocalDate from, LocalDate to);

    boolean existsByPartnerIdAndKindAndCompetenceMonth(Long partnerId, WithdrawalKind kind, LocalDate competenceMonth);

    /** Os pró-labores de um intervalo de competências, para a folha do Fator R e o DARF. */
    List<Withdrawal> findAllByCompanyIdAndKindAndCompetenceMonthBetween(Long companyId, WithdrawalKind kind,
                                                                        LocalDate from, LocalDate to);

    List<Withdrawal> findAllByUserIdOrderByCompanyIdAscPaymentDateAscIdAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Withdrawal w where w.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
