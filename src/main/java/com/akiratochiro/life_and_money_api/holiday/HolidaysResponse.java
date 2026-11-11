package com.akiratochiro.life_and_money_api.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record HolidaysResponse(
        @Schema(requiredMode = REQUIRED) int year,
        @Schema(requiredMode = REQUIRED, description = "Falso só fora do intervalo de anos coberto (1900 a 2199): a lista "
                + "vem vazia, e os prazos consideram só os fins de semana")
        boolean available,
        @Schema(requiredMode = REQUIRED, description = "Só feriados nacionais; estaduais e municipais não entram")
        List<Holiday> holidays,
        @Schema(description = "De onde vieram: da BrasilAPI ou, se ela não respondeu, do cálculo local; ausente quando não há feriados")
        Source source
) {
    public enum Source { BRASIL_API, LOCAL }
}
