package com.akiratochiro.life_and_money_api.companyoverview;

import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseService;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseMonthResponse;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseStatus;
import com.akiratochiro.life_and_money_api.invoice.RevenueService;
import com.akiratochiro.life_and_money_api.tax.ObligationKind;
import com.akiratochiro.life_and_money_api.tax.TaxOccurrenceResponse;
import com.akiratochiro.life_and_money_api.tax.TaxService;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    static final String CASH_NOTICE = "As notas entram no saldo pela data de emissão. Se o cliente paga depois, o saldo real pode estar menor.";
    /** Até quantos meses à frente um imposto de competência já começada pode vencer (o DAS de novembro vence em dezembro). */
    static final int MONTHS_AHEAD = 2;

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
     * O caixa de hoje (decisão 29), que não depende do mês escolhido:
     * <ul>
     *   <li><b>saldo</b> = saldo inicial + líquido das notas emitidas − despesas pagas (com os impostos pagos)
     *       − líquido das retiradas, do começo do dia do saldo inicial até hoje;</li>
     *   <li><b>provisão</b> = todo imposto com previsto, ainda não pago, de competência já começada, vencendo
     *       de 3 meses atrás em diante (a janela dos próximos vencimentos, para os dois cards concordarem);</li>
     *   <li><b>disponível</b> = saldo − provisão.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public CompanyCashResponse cash(Long userId, Long companyId) {
        Company company = companyService.getOwned(userId, companyId);
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);

        LocalDate from = company.getOpeningBalanceDate();
        BigDecimal revenue = revenueService.netIssuedBetween(companyId, from, today);
        BigDecimal expenses = expenseService.paidBetween(companyId, from, today);
        BigDecimal withdrawals = withdrawalService.netPaidBetween(companyId, from, today);
        BigDecimal balance = company.getOpeningBalance().add(revenue).subtract(expenses).subtract(withdrawals);

        BigDecimal currentMonth = BigDecimal.ZERO;
        BigDecimal dueSoon = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int withoutExpected = 0;
        for (YearMonth due = current.minusMonths(OVERDUE_MONTHS); !due.isAfter(current.plusMonths(MONTHS_AHEAD)); due = due.plusMonths(1)) {
            for (TaxOccurrenceResponse tax : taxService.month(userId, companyId, due).items()) {
                if (tax.declaration() || tax.payment() != null || tax.competence().isAfter(current) || nothingToPay(tax)) {
                    continue;
                }
                if (tax.expectedAmount() == null) {
                    withoutExpected++;
                } else if (tax.competence().equals(current)) {
                    currentMonth = currentMonth.add(tax.expectedAmount());
                } else if (tax.status() == TaxOccurrenceResponse.Status.OVERDUE) {
                    overdue = overdue.add(tax.expectedAmount());
                } else {
                    dueSoon = dueSoon.add(tax.expectedAmount());
                }
            }
        }
        BigDecimal provision = currentMonth.add(dueSoon).add(overdue);

        return new CompanyCashResponse(today, company.getOpeningBalance(), from,
                new CompanyCashResponse.Balance(balance, revenue, expenses, withdrawals),
                new CompanyCashResponse.Provision(provision, currentMonth, dueSoon, overdue, withoutExpected),
                balance.subtract(provision),
                governmentShare(userId, companyId, current),
                CASH_NOTICE);
    }

    /**
     * "Das notas deste mês, R$ X já é do governo": só o DAS (ou o DAS-MEI), que é o imposto sobre a
     * receita. O DARF do pró-labore é sobre a folha e fica só na provisão. Sem DAS (Lucro Presumido ou
     * Real), não há frase.
     */
    private CompanyCashResponse.GovernmentShare governmentShare(Long userId, Long companyId, YearMonth month) {
        BigDecimal revenue = revenueService.monthly(companyId, month, month)
                .getOrDefault(month, RevenueService.MonthRevenue.ZERO).total();
        for (TaxOccurrenceResponse tax : taxService.ofCompetence(userId, companyId, month)) {
            if (tax.kind() != ObligationKind.DAS && tax.kind() != ObligationKind.DAS_MEI) {
                continue;
            }
            BigDecimal amount = tax.payment() != null && tax.payment().paidAmount() != null
                    ? tax.payment().paidAmount() : tax.expectedAmount();
            if (amount == null) {
                return null;
            }
            BigDecimal rate = tax.kind() == ObligationKind.DAS && revenue.signum() > 0
                    ? amount.divide(revenue, 4, RoundingMode.HALF_UP) : null;
            return new CompanyCashResponse.GovernmentShare(month, revenue, amount, rate, tax.kind());
        }
        return null;
    }

    /**
     * O DAS de um mês sem receita sai R$ 0,00: não há guia a pagar, e listá-lo como atrasado só
     * assusta. Ele some só daqui; a aba Impostos continua mostrando (decisão 27).
     */
    static boolean nothingToPay(TaxOccurrenceResponse tax) {
        return tax.expectedAmount() != null && tax.expectedAmount().signum() == 0;
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
                if (tax.status() != TaxOccurrenceResponse.Status.PAID && !tax.dueDate().isAfter(limit) && !nothingToPay(tax)) {
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
