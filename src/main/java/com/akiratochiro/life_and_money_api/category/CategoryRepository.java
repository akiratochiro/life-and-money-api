package com.akiratochiro.life_and_money_api.category;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndUserId(Long id, Long userId);

    List<Category> findAllByUserIdOrderByNameAsc(Long userId);

    List<Category> findAllByUserIdAndArchivedFalseOrderByNameAsc(Long userId);

    boolean existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedFalse(Long userId, CategoryType type, String name);

    /**
     * Apaga tudo do usuário, para a exclusão da conta. O flush antes e o clear depois evitam que
     * alterações pendentes se percam ou que entidades já apagadas continuem no contexto.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Category x where x.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
