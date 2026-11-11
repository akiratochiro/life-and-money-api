package com.akiratochiro.life_and_money_api.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndUserId(Long id, Long userId);

    List<Company> findAllByUserId(Long userId);

    List<Company> findAllByUserIdAndArchivedFalse(Long userId);

    boolean existsByUserIdAndCnpj(Long userId, String cnpj);

    boolean existsByUserIdAndCnpjAndIdNot(Long userId, String cnpj, Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Company c where c.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
