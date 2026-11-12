package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.calendar.CalendarEvent;
import com.akiratochiro.life_and_money_api.calendar.CalendarEventSource;
import com.akiratochiro.life_and_money_api.category.CategoryType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** As contas pessoais no calendário (decisão 31), com o prazo de dia útil e o status do {@code BillService}. */
@Component
class BillCalendarSource implements CalendarEventSource {

    private final BillService service;

    BillCalendarSource(BillService service) {
        this.service = service;
    }

    @Override
    public CalendarEvent.Scope scope() {
        return CalendarEvent.Scope.PF;
    }

    @Override
    public List<CalendarEvent> eventsBetween(Long userId, LocalDate from, LocalDate to, Long companyId) {
        return service.occurrencesBetween(userId, from, to).stream()
                .filter(o -> !o.dueDate().isBefore(from) && !o.dueDate().isAfter(to))
                .map(BillCalendarSource::event)
                .toList();
    }

    /** O mesmo limite do Início: os atrasados dos últimos 12 meses, contando o corrente. */
    @Override
    public LocalDate overdueSince(LocalDate today) {
        return YearMonth.from(today).minusMonths(BillService.OVERDUE_LOOKBACK_MONTHS - 1).atDay(1);
    }

    static CalendarEvent event(BillOccurrenceResponse o) {
        boolean income = o.type() == CategoryType.INCOME;
        return new CalendarEvent("BILL:" + o.billId() + ":" + o.month(), CalendarEvent.Source.BILL, CalendarEvent.Scope.PF, null,
                o.name(), income ? "A receber" : null, o.dueDate(), o.payableUntil(), status(o.status()),
                income ? CalendarEvent.Direction.IN : CalendarEvent.Direction.OUT, o.expectedAmount(), o.paidAmount(), o.paidOn(),
                false, new CalendarEvent.CalendarLink(CalendarEvent.LinkKind.BILLS, null, o.month()));
    }

    private static CalendarEvent.Status status(BillStatus status) {
        return switch (status) {
            case PAID -> CalendarEvent.Status.PAID;
            case PENDING -> CalendarEvent.Status.PENDING;
            case OVERDUE -> CalendarEvent.Status.OVERDUE;
        };
    }
}
