package com.akiratochiro.life_and_money_api.holiday;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    @Operation(operationId = "listHolidays", summary = "Feriados nacionais do ano",
            description = "Fonte: BrasilAPI. Nunca falha: sem a BrasilAPI, responde available=false e lista vazia")
    public HolidaysResponse list(@RequestParam int year) {
        return holidayService.holidaysOf(year);
    }
}
