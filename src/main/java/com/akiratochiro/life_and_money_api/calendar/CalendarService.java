package com.akiratochiro.life_and_money_api.calendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Junta os vencimentos de todas as fontes (decisão 31). Não conhece nenhuma: recebe a lista de
 * {@link CalendarEventSource} do Spring.
 */
@Service
public class CalendarService {

    /** Cada mês de impostos recalcula o DAS de cada empresa: o front pede um mês, e isto limita o custo. */
    static final int MAX_DAYS = 62;
    static final int NEXT_DAYS = 7;

    private final List<CalendarEventSource> sources;
    private final Clock clock;

    public CalendarService(List<CalendarEventSource> sources, Clock clock) {
        this.sources = sources;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> events(Long userId, LocalDate from, LocalDate to, CalendarFilter filter, Long companyId) {
        if (to.isBefore(from)) {
            throw new InvalidCalendarRangeException("A data final precisa ser igual ou depois da inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidCalendarRangeException("O calendário mostra até " + MAX_DAYS + " dias por vez.");
        }
        List<CalendarEvent> events = new ArrayList<>();
        for (CalendarEventSource source : selected(filter, companyId)) {
            events.addAll(source.eventsBetween(userId, from, to, companyId));
        }
        events.sort(byDate());
        return events;
    }

    /**
     * Os atrasados (desde o limite de cada fonte) e o que vence de hoje até daqui a 7 dias, sem os pagos
     * nem os de nada a pagar. Os atrasados primeiro.
     */
    @Transactional(readOnly = true)
    public CalendarReminders reminders(Long userId, CalendarFilter filter, Long companyId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.plusDays(NEXT_DAYS);
        List<CalendarEvent> overdue = new ArrayList<>();
        List<CalendarEvent> nextDays = new ArrayList<>();
        for (CalendarEventSource source : selected(filter, companyId)) {
            for (CalendarEvent event : source.eventsBetween(userId, source.overdueSince(today), limit, companyId)) {
                if (!event.open()) {
                    continue;
                }
                if (event.status() == CalendarEvent.Status.OVERDUE) {
                    overdue.add(event);
                } else {
                    nextDays.add(event);
                }
            }
        }
        overdue.sort(byDate());
        nextDays.sort(byDate());
        return new CalendarReminders(overdue, nextDays, overdue.size() + nextDays.size());
    }

    /** Com uma empresa, só as fontes PJ; senão, as do escopo pedido. */
    private List<CalendarEventSource> selected(CalendarFilter filter, Long companyId) {
        return sources.stream()
                .filter(source -> companyId == null
                        ? filter == CalendarFilter.ALL || filter.name().equals(source.scope().name())
                        : source.scope() == CalendarEvent.Scope.PJ)
                .toList();
    }

    /** Por data e depois por nome, em ordem pt-BR ("Água" antes de "internet"). */
    static Comparator<CalendarEvent> byDate() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        collator.setStrength(Collator.PRIMARY);
        return Comparator.comparing(CalendarEvent::dueDate)
                .thenComparing(CalendarEvent::title, collator)
                .thenComparing(CalendarEvent::id);
    }
}
