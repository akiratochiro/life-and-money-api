package com.akiratochiro.life_and_money_api.withdrawal;

import io.swagger.v3.oas.annotations.media.Schema;

public record LaunchRequest(
        @Schema(description = "Uma categoria de receita; sem ela, \"Pró-labore\" ou \"Lucros distribuídos\" (criada se não existir)")
        Long categoryId
) {
}
