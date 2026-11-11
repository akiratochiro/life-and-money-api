package com.akiratochiro.life_and_money_api.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CompanyRegimeRepository extends JpaRepository<CompanyRegime, Long> {

    List<CompanyRegime> findAllByCompanyIdOrderByValidFromAsc(Long companyId);

    Optional<CompanyRegime> findByCompanyIdAndValidToIsNull(Long companyId);

    long countByCompanyId(Long companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyRegime r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
