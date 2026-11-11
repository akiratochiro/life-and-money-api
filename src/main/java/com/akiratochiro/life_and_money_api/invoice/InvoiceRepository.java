package com.akiratochiro.life_and_money_api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Invoice> findByCompanyIdAndAccessKey(Long companyId, String accessKey);

    boolean existsByCompanyId(Long companyId);

    List<Invoice> findAllByCompanyIdAndCompetenceDateBetweenOrderByCompetenceDateDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    @Query("select distinct year(i.competenceDate) from Invoice i where i.companyId = :companyId order by 1 desc")
    List<Integer> findYearsByCompanyId(@Param("companyId") Long companyId);

    @Query("select i from Invoice i where i.userId = :userId order by i.companyId, i.competenceDate, i.id")
    List<Invoice> findAllOfUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Invoice i where i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
