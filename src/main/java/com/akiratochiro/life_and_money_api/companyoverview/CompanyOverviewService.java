package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseMonthResponse;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseStatus;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.tax.TaxOccurrenceResponse;
import com.akiratochiro.life_and_money_api.tax.TaxService;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A visão geral da empresa (decisão 27): só lê e soma o que as outras features já calculam, como o
 * {@code dashboard} das finanças pessoais. Nenhuma delas depende deste pacote.
 */
@Service
public class CompanyOverviewService {

    static final String NOTICE = "Os impostos são estimativas do app: não substituem o seu contador.";
    /** Quantos meses de vencimento para trás entram nos atrasados (cada mês de impostos recalcula o DAS). */
    static final int OVERDUE_MONTHS = 3;
    static final int UPCOMING_DAYS = 30;

    private final CompanyService companyService;
    private final RevenueService revenueService;
    private final CompanyExpenseService expenseService;
    private final TaxService taxService;
    private final WithdrawalService withdrawalService;
    private final Clock clock;

    public CompanyOverviewService(CompanyService companyService, RevenueService revenueService, CompanyExpenseService expenseService,
                                  TaxService taxService, WithdrawalService withdrawalService, Clock clock) {
        this.companyService = companyService;
        this.revenueService = revenueService;
        this.expenseService = expenseService;
        this.taxService = taxService;
        this.withdrawalService = withdrawalService;
        this.clock = clock;
    }

    /**
     * Os cards do mês. Os impostos são os da competência (o DAS de novembro vence em dezembro, mas é
     * sobre a receita de novembro), pelo valor pago quando já foi pago; as despesas não incluem a
     * categoria Impostos, para o imposto pago não contar duas vezes.
     */
    @Transactional(readOnly = true)
    public CompanyOverviewResponse overview(Long userId, Long companyId, YearMonth month) {
        companyService.getOwned(userId, companyId);
        BigDecimal revenue = revenueService.monthly(companyId, month, month)
                .getOrDefault(month, RevenueService.MonthRevenue.ZERO).total();
        CompanyExpenseService.MonthTotals expenses = expenseService.monthTotals(userId, companyId, month);

        BigDecimal taxesPaid = BigDecimal.ZERO;
        BigDecimal taxesExpected = BigDecimal.ZERO;
        int withoutExpected = 0;
        for (TaxOccurrenceResponse tax : taxService.ofCompetence(userId, companyId, month)) {
            if (tax.payment() != null && tax.payment().paidAmount() != null) {
                taxesPaid = taxesPaid.add(tax.payment().paidAmount());
            } else if (tax.expectedAmount() != null) {
                taxesExpected = taxesExpected.add(tax.expectedAmount());
            } else {
                withoutExpected++;
            }
        }
        BigDecimal taxes = taxesPaid.add(taxesExpected);
        WithdrawalService.PaidTotal withdrawals = withdrawalService.paidBetween(companyId, month.atDay(1), month.atEndOfMonth());

        return new CompanyOverviewResponse(month, !month.isBefore(YearMonth.now(clock)), revenue,
                new CompanyOverviewResponse.Expenses(expenses.paid(), expenses.pending(), expenses.total()),
                new CompanyOverviewResponse.Taxes(taxesPaid, taxesExpected, taxes, withoutExpected),
                revenue.subtract(expenses.total()).subtract(taxes),
                new CompanyOverviewResponse.Withdrawals(withdrawals.total(), withdrawals.count()),
                NOTICE);
    }

    /**
     * O que falta pagar: os atrasados dos últimos 3 meses de vencimento e o que vence nos próximos 30
     * dias, impostos e despesas fixas juntos, os atrasados primeiro. A Fase 9 (calendário fiscal)
     * generaliza esta fonte.
     */
    @Transactional(readOnly = true)
    public List<UpcomingItem> upcoming(Long userId, Long companyId) {
        companyService.getOwned(userId, companyId);
        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.plusDays(UPCOMING_DAYS);
        YearMonth first = YearMonth.from(today).minusMonths(OVERDUE_MONTHS);
        YearMonth last = YearMonth.from(limit);

        List<UpcomingItem> items = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            for (TaxOccurrenceResponse tax : taxService.month(userId, companyId, month).items()) {
                if (tax.status() != TaxOccurrenceResponse.Status.PAID && !tax.dueDate().isAfter(limit)) {
                    items.add(new UpcomingItem(UpcomingItem.Source.TAX, tax.name(), tax.dueDate(), tax.expectedAmount(),
                            tax.status() == TaxOccurrenceResponse.Status.OVERDUE ? UpcomingItem.Status.OVERDUE : UpcomingItem.Status.PENDING,
                            month));
                }
            }
            for (ExpenseMonthResponse.Item expense : expenseService.month(userId, companyId, month).items()) {
                if (expense.kind() == ExpenseMonthResponse.Kind.RECURRING && expense.status() != ExpenseStatus.PAID
                        && !expense.dueDate().isAfter(limit)) {
                    items.add(new UpcomingItem(UpcomingItem.Source.EXPENSE, expense.name(), expense.dueDate(), expense.amount(),
                            expense.status() == ExpenseStatus.OVERDUE ? UpcomingItem.Status.OVERDUE : UpcomingItem.Status.PENDING,
                            month));
                }
            }
        }
        items.sort(Comparator.comparing((UpcomingItem item) -> item.status() != UpcomingItem.Status.OVERDUE)
                .thenComparing(UpcomingItem::dueDate)
                .thenComparing(UpcomingItem::name));
        return items;
    }
}
