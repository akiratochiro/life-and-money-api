package com.akiratochiro.life_and_money_api.calendar;

import com.akiratochiro.life_and_money_api.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "calendar", summary = "Os vencimentos de contas pessoais, impostos e despesas das empresas no período (até 62 dias)")
    public List<CalendarEvent> events(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "ALL") CalendarFilter scope,
                                      @RequestParam(required = false) Long companyId) {
        return service.events(CurrentUser.id(jwt), from, to, scope, companyId);
    }

    @GetMapping("/reminders")
    @Operation(operationId = "calendarReminders", summary = "O que pede atenção: os atrasados e os próximos 7 dias")
    public CalendarReminders reminders(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "ALL") CalendarFilter scope,
                                       @RequestParam(required = false) Long companyId) {
        return service.reminders(CurrentUser.id(jwt), scope, companyId);
    }
}
