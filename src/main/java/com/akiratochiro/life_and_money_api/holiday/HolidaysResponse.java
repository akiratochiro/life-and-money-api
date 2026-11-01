package com.akiratochiro.life_and_money_api.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record HolidaysResponse(
        @Schema(requiredMode = REQUIRED) int year,
        @Schema(requiredMode = REQUIRED, description = "Falso quando a BrasilAPI não respondeu: a lista vem vazia, "
                + "e os prazos das contas consideram só os fins de semana")
        boolean available,
        @Schema(requiredMode = REQUIRED, description = "Só feriados nacionais; estaduais e municipais não entram")
        List<Holiday> holidays
) {
}
