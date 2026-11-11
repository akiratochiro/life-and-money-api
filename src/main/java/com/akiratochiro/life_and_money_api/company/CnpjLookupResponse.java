package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CnpjLookupResponse(
        @Schema(requiredMode = REQUIRED, description = "Falso quando a consulta não respondeu; o cadastro segue à mão")
        boolean available,
        @Schema(requiredMode = REQUIRED, description = "Falso quando a consulta não conhece o CNPJ")
        boolean found,
        @Schema(description = "Razão social") String name,
        @Schema(description = "Nome fantasia") String tradeName,
        LocalDate openingDate,
        @Schema(description = "Só quando o código da Receita leva a uma natureza sem ambiguidade")
        LegalNature legalNature
) {
    static CnpjLookupResponse unavailable() {
        return new CnpjLookupResponse(false, false, null, null, null, null);
    }

    static CnpjLookupResponse notFound() {
        return new CnpjLookupResponse(true, false, null, null, null, null);
    }

    static CnpjLookupResponse of(CnpjData data) {
        return new CnpjLookupResponse(true, true, data.name(), data.tradeName(), data.openingDate(), data.legalNature());
    }
}
