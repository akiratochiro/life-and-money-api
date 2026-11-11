package com.akiratochiro.life_and_money_api.companyexpense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CompanyExpenseCategoryRepository extends JpaRepository<CompanyExpenseCategory, Long> {

    Optional<CompanyExpenseCategory> findByIdAndCompanyId(Long id, Long companyId);

    Optional<CompanyExpenseCategory> findByCompanyIdAndSystemKey(Long companyId, ExpenseCategorySystemKey key);

    List<CompanyExpenseCategory> findAllByCompanyId(Long companyId);

    List<CompanyExpenseCategory> findAllByCompanyIdAndArchivedFalse(Long companyId);

    List<CompanyExpenseCategory> findAllByUserIdOrderByCompanyIdAscIdAsc(Long userId);

    boolean existsByCompanyIdAndNameIgnoreCaseAndArchivedFalse(Long companyId, String name);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyExpenseCategory c where c.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
