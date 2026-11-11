package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.taxengine.IrrfBracket;
import com.akiratochiro.life_and_money_api.taxengine.SimplesBracket;
import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Os parâmetros que valem numa data, cada um com a vigência e a fonte legal. */
public record TaxParametersResponse(
        @Schema(requiredMode = REQUIRED) LocalDate date,
        @Schema(requiredMode = REQUIRED) List<Parameter> parameters,
        @Schema(requiredMode = REQUIRED) List<Simples> simplesBrackets,
        @Schema(requiredMode = REQUIRED) List<Irrf> irrfBrackets
) {
    public record Parameter(
            @Schema(requiredMode = REQUIRED) TaxParameter key,
            @Schema(requiredMode = REQUIRED) BigDecimal value,
            @Schema(requiredMode = REQUIRED) LocalDate validFrom,
            @Schema(requiredMode = REQUIRED) String source
    ) {
    }

    public record Simples(
            @Schema(requiredMode = REQUIRED) SimplesBracket bracket,
            @Schema(requiredMode = REQUIRED) LocalDate validFrom,
            @Schema(requiredMode = REQUIRED) String source
    ) {
    }

    public record Irrf(
            @Schema(requiredMode = REQUIRED) IrrfBracket bracket,
            @Schema(requiredMode = REQUIRED) LocalDate validFrom,
            @Schema(requiredMode = REQUIRED) String source
    ) {
    }
}
