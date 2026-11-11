package com.akiratochiro.life_and_money_api.companyexpense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    Optional<RecurringExpense> findByIdAndCompanyId(Long id, Long companyId);

    List<RecurringExpense> findAllByCompanyIdOrderByIdAsc(Long companyId);

    List<RecurringExpense> findAllByUserIdOrderByIdAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecurringExpense r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
