package com.akiratochiro.life_and_money_api.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findAllByCompanyIdOrderByIdAsc(Long companyId);

    Optional<Partner> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyId(Long companyId);

    boolean existsByCompanyIdAndIsMeTrue(Long companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Partner p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
