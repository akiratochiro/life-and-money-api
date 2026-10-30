package com.akiratochiro.life_and_money_api.transaction;

import org.springframework.data.jpa.repository.Modifying;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>{
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    List<Transaction> findAllByUserIdOrderByTransactionDateAscIdAsc(Long userId);

    List<Transaction> findAllByUserIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            Long userId, LocalDate start, LocalDate end);
    @Query("""
        select new com.akiratochiro.life_and_money_api.transaction.CategoryTotal(c.id, c.name, sum(t.amount))
        from Transaction t
        join Category c on c.id = t.categoryId
        where t.userId = :userId
          and c.type = :type
          and t.transactionDate between :start and :end
        group by c.id, c.name
        order by sum(t.amount) desc
        """)
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId,
                                      @Param("type") CategoryType type,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    @Query("""
        select new com.akiratochiro.life_and_money_api.transaction.MonthlyTypeTotal(
            extract(year from t.transactionDate),
            extract(month from t.transactionDate),
            c.type,
            sum(t.amount))
        from Transaction t
        join Category c on c.id = t.categoryId
        where t.userId = :userId
          and t.transactionDate between :start and :end
        group by extract(year from t.transactionDate),
                 extract(month from t.transactionDate),
                 c.type
        """)
    List<MonthlyTypeTotal> sumByMonthAndType(@Param("userId") Long userId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);




    @Query("""
        select new com.akiratochiro.life_and_money_api.transaction.GoalTotal(t.goalId, sum(t.amount))
        from Transaction t
        where t.userId = :userId
          and t.goalId is not null
        group by t.goalId
        """)
    List<GoalTotal> sumByGoal(@Param("userId") Long userId);

    /**
     * Apaga tudo do usuário, para a exclusão da conta. O flush antes e o clear depois evitam que
     * alterações pendentes se percam ou que entidades já apagadas continuem no contexto.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
