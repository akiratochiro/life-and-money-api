package com.akiratochiro.life_and_money_api.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface TaxObligationRepository extends JpaRepository<TaxObligation, Long> {

    List<TaxObligation> findAllByCompanyIdOrderByIdAsc(Long companyId);

    Optional<TaxObligation> findByIdAndCompanyId(Long id, Long companyId);

    List<TaxObligation> findAllByUserIdOrderByCompanyIdAscIdAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TaxObligation o where o.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
