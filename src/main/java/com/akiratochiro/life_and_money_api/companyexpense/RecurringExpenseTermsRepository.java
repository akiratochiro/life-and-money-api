package com.akiratochiro.life_and_money_api.companyexpense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface RecurringExpenseTermsRepository extends JpaRepository<RecurringExpenseTerms, Long> {

    List<RecurringExpenseTerms> findAllByRecurringExpenseIdInOrderByValidFromAsc(Collection<Long> ids);

    Optional<RecurringExpenseTerms> findByRecurringExpenseIdAndValidToIsNull(Long recurringExpenseId);

    List<RecurringExpenseTerms> findAllByCategoryIdAndValidToIsNull(Long categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecurringExpenseTerms t where t.recurringExpenseId = :id")
    int deleteAllByRecurringExpenseId(@Param("id") Long recurringExpenseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecurringExpenseTerms t where t.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
