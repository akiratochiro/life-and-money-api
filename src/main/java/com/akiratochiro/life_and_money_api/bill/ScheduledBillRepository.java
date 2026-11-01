package com.akiratochiro.life_and_money_api.bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface ScheduledBillRepository extends JpaRepository<ScheduledBill, Long> {

    Optional<ScheduledBill> findByIdAndUserId(Long id, Long userId);

    List<ScheduledBill> findAllByUserIdOrderByStartMonthAscIdAsc(Long userId);

    /** As contas com pelo menos um mês entre {@code firstMonth} e {@code lastMonth}. */
    @Query("""
            select b from ScheduledBill b
            where b.userId = :userId
              and b.startMonth <= :lastMonth
              and (b.endMonth is null or b.endMonth >= :firstMonth)
            """)
    List<ScheduledBill> findAllActiveBetween(@Param("userId") Long userId,
                                             @Param("firstMonth") LocalDate firstMonth,
                                             @Param("lastMonth") LocalDate lastMonth);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ScheduledBill x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
