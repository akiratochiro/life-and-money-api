package com.akiratochiro.life_and_money_api.companyexpense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface CompanyExpenseRepository extends JpaRepository<CompanyExpense, Long> {

    Optional<CompanyExpense> findByIdAndCompanyId(Long id, Long companyId);

    Optional<CompanyExpense> findByRecurringExpenseIdAndOccurrenceMonth(Long recurringExpenseId, LocalDate month);

    /** As avulsas do mês, pela data. */
    @Query("""
            select e from CompanyExpense e
             where e.companyId = :companyId and e.recurringExpenseId is null
               and e.expenseDate between :from and :to
            """)
    List<CompanyExpense> findOneOffsBetween(@Param("companyId") Long companyId,
                                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Os pagamentos de fixas do mês, pelo mês do vencimento (o aluguel de outubro pago em 3 de novembro é de outubro). */
    List<CompanyExpense> findAllByCompanyIdAndOccurrenceMonth(Long companyId, LocalDate month);

    @Query("select max(e.occurrenceMonth) from CompanyExpense e where e.recurringExpenseId = :id")
    Optional<LocalDate> findLastPaidMonth(@Param("id") Long recurringExpenseId);

    List<CompanyExpense> findAllByUserIdOrderByCompanyIdAscExpenseDateAscIdAsc(Long userId);

    /** As despesas das categorias marcadas como folha (salários, encargos, FGTS), para o Fator R. */
    @Query("""
            select e from CompanyExpense e, CompanyExpenseCategory c
             where c.id = e.categoryId and c.countsAsPayroll = true
               and e.companyId = :companyId and e.expenseDate between :from and :to
            """)
    List<CompanyExpense> findPayrollBetween(@Param("companyId") Long companyId, @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    List<CompanyExpense> findAllByCompanyIdAndIdIn(Long companyId, java.util.Collection<Long> ids);

    @Query("select e.attachmentId from CompanyExpense e where e.userId = :userId and e.attachmentId is not null")
    List<Long> findAttachmentIdsOfUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyExpense e where e.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
