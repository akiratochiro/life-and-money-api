package com.akiratochiro.life_and_money_api.goal;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    Optional<SavingsGoal> findByIdAndUserId(Long id, Long userId);

    List<SavingsGoal> findAllByUserIdOrderByDeadlineAsc(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Apaga tudo do usuário, para a exclusão da conta. O flush antes e o clear depois evitam que
     * alterações pendentes se percam ou que entidades já apagadas continuem no contexto.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SavingsGoal x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
