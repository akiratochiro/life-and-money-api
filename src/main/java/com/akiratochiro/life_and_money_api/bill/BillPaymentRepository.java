package com.akiratochiro.life_and_money_api.bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    List<BillPayment> findAllByUserIdOrderByBillIdAscDueMonthAsc(Long userId);

    @Query("""
            select p from BillPayment p
            where p.userId = :userId
              and p.billId in :billIds
              and p.dueMonth between :firstMonth and :lastMonth
            """)
    List<BillPayment> findAllInMonths(@Param("userId") Long userId,
                                      @Param("billIds") Collection<Long> billIds,
                                      @Param("firstMonth") LocalDate firstMonth,
                                      @Param("lastMonth") LocalDate lastMonth);

    Optional<BillPayment> findByUserIdAndBillIdAndDueMonth(Long userId, Long billId, LocalDate dueMonth);

    boolean existsByUserIdAndBillIdAndDueMonth(Long userId, Long billId, LocalDate dueMonth);

    /** O mês pago mais recente da conta, para não encerrá-la antes de um pagamento. */
    @Query("select max(p.dueMonth) from BillPayment p where p.billId = :billId")
    Optional<LocalDate> findLastPaidMonth(@Param("billId") Long billId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BillPayment x where x.userId = :userId and x.transactionId = :transactionId")
    int deleteByTransactionId(@Param("userId") Long userId, @Param("transactionId") Long transactionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BillPayment x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
