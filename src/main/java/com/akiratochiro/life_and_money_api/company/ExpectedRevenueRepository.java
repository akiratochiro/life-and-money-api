package com.akiratochiro.life_and_money_api.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ExpectedRevenueRepository extends JpaRepository<ExpectedRevenue, Long> {

    Optional<ExpectedRevenue> findByCompanyIdAndValidToIsNull(Long companyId);

    List<ExpectedRevenue> findAllByCompanyId(Long companyId);

    List<ExpectedRevenue> findAllByUserIdOrderByCompanyIdAscValidFromAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExpectedRevenue e where e.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
