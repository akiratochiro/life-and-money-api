package com.akiratochiro.life_and_money_api.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface TaxPaymentRepository extends JpaRepository<TaxPayment, Long> {

    List<TaxPayment> findAllByObligationIdIn(Collection<Long> obligationIds);

    Optional<TaxPayment> findByObligationIdAndCompetenceMonth(Long obligationId, LocalDate competence);

    Optional<TaxPayment> findByExpenseId(Long expenseId);

    boolean existsByObligationId(Long obligationId);

    List<TaxPayment> findAllByUserIdOrderByObligationIdAscCompetenceMonthAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TaxPayment p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
