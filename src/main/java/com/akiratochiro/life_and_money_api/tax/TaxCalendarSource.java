package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.calendar.CalendarEvent;
import com.akiratochiro.life_and_money_api.calendar.CalendarEventSource;
import com.akiratochiro.life_and_money_api.company.Company;
import com.akiratochiro.life_and_money_api.company.CompanyDetails;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Os impostos e as declarações das empresas ativas no calendário (decisão 31). As ocorrências vêm de
 * {@link TaxService#month}, por mês de vencimento, com o DARF já antecipado e o status pronto.
 */
@Component
class TaxCalendarSource implements CalendarEventSource {

    /** Cada mês de impostos recalcula o DAS: os mesmos 3 meses do "Próximos vencimentos" (decisão 27). */
    static final int OVERDUE_MONTHS = 3;
    private static final DateTimeFormatter COMPETENCE = DateTimeFormatter.ofPattern("MM/yyyy");

    private final TaxService taxService;
    private final CompanyService companyService;

    TaxCalendarSource(TaxService taxService, CompanyService companyService) {
        this.taxService = taxService;
        this.companyService = companyService;
    }

    @Override
    public CalendarEvent.Scope scope() {
        return CalendarEvent.Scope.PJ;
    }

    @Override
    public List<CalendarEvent> eventsBetween(Long userId, LocalDate from, LocalDate to, Long companyId) {
        List<CalendarEvent> events = new ArrayList<>();
        for (Company company : companies(userId, companyId)) {
            CalendarEvent.CalendarCompany who = new CalendarEvent.CalendarCompany(company.getId(), company.getName(), company.getEmoji());
            for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
                for (TaxOccurrenceResponse tax : taxService.month(userId, company.getId(), month).items()) {
                    if (!tax.dueDate().isBefore(from) && !tax.dueDate().isAfter(to)) {
                        events.add(event(who, tax, month));
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

    /** As empresas ativas do usuário, ou só a pedida (404 se não for dele; arquivada, nenhuma). */
    private List<Company> companies(Long userId, Long companyId) {
        if (companyId != null) {
            Company company = companyService.getOwned(userId, companyId);
            return company.isArchived() ? List.of() : List.of(company);
        }
        return companyService.list(userId, false).stream().map(CompanyDetails::company).toList();
    }

    static CalendarEvent event(CalendarEvent.CalendarCompany company, TaxOccurrenceResponse tax, YearMonth dueMonth) {
        TaxOccurrenceResponse.Payment payment = tax.payment();
        CalendarEvent.Status status = switch (tax.status()) {
            case PAID -> CalendarEvent.Status.PAID;
            case PENDING -> CalendarEvent.Status.PENDING;
            case OVERDUE -> CalendarEvent.Status.OVERDUE;
        };
        String detail = tax.declaration()
                ? "Ano-calendário " + tax.competence().getYear()
                : "Competência " + tax.competence().format(COMPETENCE);
        boolean nothingToPay = tax.expectedAmount() != null && tax.expectedAmount().signum() == 0;
        return new CalendarEvent("TAX:" + tax.obligationId() + ":" + tax.competence(),
                tax.declaration() ? CalendarEvent.Source.DECLARATION : CalendarEvent.Source.TAX, CalendarEvent.Scope.PJ, company,
                tax.name(), detail, tax.dueDate(), tax.dueDate(), status, CalendarEvent.Direction.OUT,
                tax.declaration() ? null : tax.expectedAmount(),
                payment == null ? null : payment.paidAmount(),
                payment == null ? null : (payment.paidDate() != null ? payment.paidDate() : payment.deliveredOn()),
                nothingToPay,
                new CalendarEvent.CalendarLink(CalendarEvent.LinkKind.COMPANY_TAXES, company.id(), dueMonth));
    }
}
