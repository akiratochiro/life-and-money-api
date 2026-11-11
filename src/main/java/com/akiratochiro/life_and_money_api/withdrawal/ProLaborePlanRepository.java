package com.akiratochiro.life_and_money_api.withdrawal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ProLaborePlanRepository extends JpaRepository<ProLaborePlan, Long> {

    Optional<ProLaborePlan> findByPartnerIdAndValidToIsNull(Long partnerId);

    List<ProLaborePlan> findAllByPartnerIdIn(Collection<Long> partnerIds);

    List<ProLaborePlan> findAllByUserIdOrderByPartnerIdAscValidFromAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProLaborePlan p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
