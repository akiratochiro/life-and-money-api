package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.calendar.CalendarEvent;
import com.akiratochiro.life_and_money_api.calendar.CalendarEventSource;
import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyDetails;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * As despesas fixas das empresas ativas no calendário (decisão 31), como no "Próximos vencimentos". A
 * avulsa é um fato já pago, sem vencimento, e fica fora.
 */
@Component
class CompanyExpenseCalendarSource implements CalendarEventSource {

    static final int OVERDUE_MONTHS = 3;

    private final CompanyExpenseService expenseService;
    private final CompanyService companyService;

    CompanyExpenseCalendarSource(CompanyExpenseService expenseService, CompanyService companyService) {
        this.expenseService = expenseService;
        this.companyService = companyService;
    }

    @Override
    public CalendarEvent.Scope scope() {
        return CalendarEvent.Scope.PJ;
    }

    @Override
    public List<CalendarEvent> eventsBetween(Long userId, LocalDate from, LocalDate to, Long companyId) {
        List<Company> companies;
        if (companyId != null) {
            Company company = companyService.getOwned(userId, companyId);
            companies = company.isArchived() ? List.of() : List.of(company);
        } else {
            companies = companyService.list(userId, false).stream().map(CompanyDetails::company).toList();
        }
        List<CalendarEvent> events = new ArrayList<>();
        for (Company company : companies) {
            CalendarEvent.CalendarCompany who = new CalendarEvent.CalendarCompany(company.getId(), company.getName(), company.getEmoji());
            for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
                for (ExpenseMonthResponse.Item item : expenseService.month(userId, company.getId(), month).items()) {
                    if (item.kind() == ExpenseMonthResponse.Kind.RECURRING && item.dueDate() != null
                            && !item.dueDate().isBefore(from) && !item.dueDate().isAfter(to)) {
                        events.add(event(who, item, month));
                    }
                }
            }
        }
        return events;
    }

    @Override
    public LocalDate overdueSince(LocalDate today) {
        return YearMonth.from(today).minusMonths(OVERDUE_MONTHS).atDay(1);
    }

    static CalendarEvent event(CalendarEvent.CalendarCompany company, ExpenseMonthResponse.Item item, YearMonth month) {
        boolean paid = item.status() == ExpenseStatus.PAID;
        CalendarEvent.Status status = switch (item.status()) {
            case PAID -> CalendarEvent.Status.PAID;
            case PENDING -> CalendarEvent.Status.PENDING;
            case OVERDUE -> CalendarEvent.Status.OVERDUE;
        };
        return new CalendarEvent("COMPANY_EXPENSE:" + item.recurringExpenseId() + ":" + month, CalendarEvent.Source.COMPANY_EXPENSE,
                CalendarEvent.Scope.PJ, company, item.name(), "Despesa fixa", item.dueDate(), item.dueDate(), status,
                CalendarEvent.Direction.OUT, item.expectedAmount() != null ? item.expectedAmount() : item.amount(),
                paid ? item.amount() : null, paid ? item.date() : null, false,
                new CalendarEvent.CalendarLink(CalendarEvent.LinkKind.COMPANY_EXPENSES, company.id(), month));
    }
}
