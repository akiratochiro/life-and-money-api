package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record ChangeRegimeRequest(
        @Schema(description = "Primeiro mês do enquadramento novo. Precisa ser depois do início do atual; "
                + "pode ser no passado (para registrar o histórico) ou no futuro (para agendar)",
                example = "2025-03")
        @NotNull(message = "Informe a partir de que mês o novo enquadramento vale.")
        YearMonth validFrom,

        @NotNull(message = "Escolha a natureza jurídica.")
        LegalNature legalNature,

        @NotNull(message = "Escolha o regime tributário.")
        TaxRegime taxRegime,

        @Schema(description = "Obrigatório no Simples Nacional, ausente nos outros regimes")
        AnnexConfig annexConfig,

        @Schema(description = "Obrigatória no MEI, ausente nas outras naturezas")
        MeiActivity meiActivity
) {
    RegimeChange toChange() {
        return new RegimeChange(validFrom, legalNature, taxRegime, annexConfig, meiActivity);
    }
}
