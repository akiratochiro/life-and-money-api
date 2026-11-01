package com.akiratochiro.life_and_money_api.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Um feriado nacional, com o nome que a BrasilAPI dá a ele. */
public record Holiday(
        @Schema(requiredMode = REQUIRED) LocalDate date,
        @Schema(requiredMode = REQUIRED, example = "Finados") String name
) {
}
