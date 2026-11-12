package com.akiratochiro.life_and_money_api.calendar;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A junção, o filtro e os lembretes, com fontes falsas. Hoje é 12/11/2026. */
class CalendarServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    /** Uma fonte que devolve os eventos dela dentro do intervalo, e anota o que recebeu. */
    static final class FakeSource implements CalendarEventSource {
        final CalendarEvent.Scope scope;
        final List<CalendarEvent> events;
        final int lookbackMonths;
        final List<Object[]> calls = new ArrayList<>();

        FakeSource(CalendarEvent.Scope scope, int lookbackMonths, CalendarEvent... events) {
            this.scope = scope;
            this.lookbackMonths = lookbackMonths;
            this.events = List.of(events);
        }

        @Override
        public CalendarEvent.Scope scope() {
            return scope;
        }

        @Override
        public List<CalendarEvent> eventsBetween(Long userId, LocalDate from, LocalDate to, Long companyId) {
            calls.add(new Object[]{from, to, companyId});
            return events.stream().filter(e -> !e.dueDate().isBefore(from) && !e.dueDate().isAfter(to))
                    .filter(e -> companyId == null || (e.company() != null && e.company().id().equals(companyId)))
                    .toList();
        }

        @Override
        public LocalDate overdueSince(LocalDate today) {
            return YearMonth.from(today).minusMonths(lookbackMonths).atDay(1);
        }
    }

    static CalendarEvent event(String title, CalendarEvent.Scope scope, String due, CalendarEvent.Status status, boolean nothingToPay) {
        LocalDate date = LocalDate.parse(due);
        CalendarEvent.CalendarCompany company = scope == CalendarEvent.Scope.PJ ? new CalendarEvent.CalendarCompany(3L, "Ana", "🎨") : null;
        return new CalendarEvent(scope + ":" + title, scope == CalendarEvent.Scope.PJ ? CalendarEvent.Source.TAX : CalendarEvent.Source.BILL,
                scope, company, title, null, date, date, status, CalendarEvent.Direction.OUT,
                nothingToPay ? BigDecimal.ZERO : BigDecimal.TEN, null, null, nothingToPay,
                new CalendarEvent.CalendarLink(CalendarEvent.LinkKind.BILLS, null, YearMonth.from(date)));
    }

    final FakeSource pf = new FakeSource(CalendarEvent.Scope.PF, 11,
            event("Internet", CalendarEvent.Scope.PF, "2026-11-15", CalendarEvent.Status.PENDING, false),
            event("água", CalendarEvent.Scope.PF, "2026-11-15", CalendarEvent.Status.PENDING, false),
            event("Aluguel antigo", CalendarEvent.Scope.PF, "2026-03-10", CalendarEvent.Status.OVERDUE, false),
            event("Luz", CalendarEvent.Scope.PF, "2026-11-05", CalendarEvent.Status.PAID, false));
    final FakeSource pj = new FakeSource(CalendarEvent.Scope.PJ, 3,
            event("DAS", CalendarEvent.Scope.PJ, "2026-11-10", CalendarEvent.Status.OVERDUE, false),
            event("DAS zero", CalendarEvent.Scope.PJ, "2026-11-13", CalendarEvent.Status.PENDING, true),
            event("DARF", CalendarEvent.Scope.PJ, "2026-11-19", CalendarEvent.Status.PENDING, false),
            event("DAS de março", CalendarEvent.Scope.PJ, "2026-03-20", CalendarEvent.Status.OVERDUE, false),
            event("Taxa", CalendarEvent.Scope.PJ, "2026-11-25", CalendarEvent.Status.PENDING, false));
    final CalendarService service = new CalendarService(List.of(pf, pj), CLOCK);

    @Test
    void joinsAllSourcesByDateThenByNameInPortuguese() {
        var events = service.events(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), CalendarFilter.ALL, null);

        assertThat(events).extracting(CalendarEvent::title)
                .containsExactly("Luz", "DAS", "DAS zero", "água", "Internet", "DARF", "Taxa");
    }

    @Test
    void theFilterPicksTheSources() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);

        assertThat(service.events(1L, from, to, CalendarFilter.PF, null)).allMatch(e -> e.scope() == CalendarEvent.Scope.PF);
        assertThat(service.events(1L, from, to, CalendarFilter.PJ, null)).allMatch(e -> e.scope() == CalendarEvent.Scope.PJ);
    }

    @Test
    void aCompanyAsksOnlyThePjSources() {
        var events = service.events(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), CalendarFilter.ALL, 3L);

        assertThat(events).allMatch(e -> e.scope() == CalendarEvent.Scope.PJ).hasSize(4);
        assertThat(pf.calls).isEmpty();
        assertThat(pj.calls.getFirst()[2]).isEqualTo(3L);
    }

    @Test
    void theRangeIsLimited() {
        assertThatThrownBy(() -> service.events(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 2), CalendarFilter.ALL, null))
                .isInstanceOf(InvalidCalendarRangeException.class)
                .hasMessage("O calendário mostra até 62 dias por vez.");
        assertThatThrownBy(() -> service.events(1L, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), CalendarFilter.ALL, null))
                .isInstanceOf(InvalidCalendarRangeException.class);
        assertThat(service.events(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 1), CalendarFilter.ALL, null)).isNotEmpty();
    }

    /**
     * Atrasados: o DAS de 10/11 e o aluguel de março (a fonte PF olha 11 meses para trás); o DAS de março
     * fica fora (a PJ olha só 3). Próximos 7 dias (até 19/11): água, Internet e DARF. Ficam fora o pago,
     * o de nada a pagar e a taxa de 25/11.
     */
    @Test
    void remindersAreTheOverdueWithinEachSourcesLimitAndTheNextSevenDays() {
        var reminders = service.reminders(1L, CalendarFilter.ALL, null);

        assertThat(reminders.overdue()).extracting(CalendarEvent::title).containsExactly("Aluguel antigo", "DAS");
        assertThat(reminders.nextDays()).extracting(CalendarEvent::title).containsExactly("água", "Internet", "DARF");
        assertThat(reminders.count()).isEqualTo(5);
        assertThat(pj.calls.getFirst()).containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 19), null);
    }
}
