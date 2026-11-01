package com.akiratochiro.life_and_money_api.bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface BillTermsRepository extends JpaRepository<BillTerms, Long> {

    List<BillTerms> findAllByUserIdAndBillIdInOrderByValidFromAsc(Long userId, Collection<Long> billIds);

    List<BillTerms> findAllByUserIdOrderByBillIdAscValidFromAsc(Long userId);

    List<BillTerms> findAllByUserIdAndCategoryIdAndValidToIsNull(Long userId, Long categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BillTerms x where x.billId = :billId")
    int deleteAllByBillId(@Param("billId") Long billId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BillTerms x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
