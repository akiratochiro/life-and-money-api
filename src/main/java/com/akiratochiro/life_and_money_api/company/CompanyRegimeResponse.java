package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CompanyRegimeResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) LegalNature legalNature,
        @Schema(requiredMode = REQUIRED) TaxRegime taxRegime,
        @Schema(description = "Só no Simples Nacional") AnnexConfig annexConfig,
        @Schema(description = "Só no MEI") MeiActivity meiActivity,
        @Schema(requiredMode = REQUIRED, example = "2024-03") YearMonth validFrom,
        @Schema(description = "Ausente na vigência mais recente, que não tem fim", example = "2025-02")
        YearMonth validTo
) {
    public static CompanyRegimeResponse from(CompanyRegime regime) {
        return new CompanyRegimeResponse(regime.getId(), regime.getLegalNature(), regime.getTaxRegime(),
                regime.getAnnexConfig(), regime.getMeiActivity(), regime.getValidFrom(), regime.getValidTo());
    }
}
