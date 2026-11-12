package com.akiratochiro.life_and_money_api.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /** Só a data e o valor de cada nota ativa: o gráfico não precisa do resto. */
    @Query("""
            select new com.akiratochiro.life_and_money_api.invoice.RevenueSeries$Entry(
                       i.competenceDate, i.serviceAmount - i.unconditionalDiscountAmount)
              from Invoice i
             where i.companyId = :companyId
               and i.status = com.akiratochiro.life_and_money_api.invoice.InvoiceStatus.ATIVA
               and i.competenceDate between :from and :to
            """)
    List<RevenueSeries.Entry> findActiveRevenue(@Param("companyId") Long companyId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * O que as notas ativas emitidas no intervalo puseram na conta: o líquido, porque as retenções o
     * tomador paga direto ao governo. É o caixa da Fase 7, pela data de emissão (decisão 29).
     */
    @Query("""
            select coalesce(sum(i.netAmount), 0)
              from Invoice i
             where i.companyId = :companyId
               and i.status = com.akiratochiro.life_and_money_api.invoice.InvoiceStatus.ATIVA
               and i.issueDate between :from and :to
            """)
    BigDecimal sumActiveNetIssuedBetween(@Param("companyId") Long companyId,
                                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** A receita das notas ativas com ISS retido pelo tomador: a parte do ISS sai do DAS dela. */
    @Query("""
            select new com.akiratochiro.life_and_money_api.invoice.RevenueSeries$Entry(
                       i.competenceDate, i.serviceAmount - i.unconditionalDiscountAmount)
              from Invoice i
             where i.companyId = :companyId
               and i.status = com.akiratochiro.life_and_money_api.invoice.InvoiceStatus.ATIVA
               and i.issWithheldAmount > 0
               and i.competenceDate between :from and :to
            """)
    List<RevenueSeries.Entry> findActiveRevenueWithIssWithheld(@Param("companyId") Long companyId,
                                                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select i from Invoice i where i.userId = :userId order by i.companyId, i.competenceDate, i.id")
    List<Invoice> findAllOfUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Invoice i where i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
