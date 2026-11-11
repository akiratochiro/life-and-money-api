package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CompanyResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) String emoji,
        @Schema(description = "Sem pontuação; ausente quando não foi informado", example = "12ABC34501DE35")
        String cnpj,
        @Schema(requiredMode = REQUIRED) LocalDate openingDate,
        @Schema(requiredMode = REQUIRED,
                description = "Se a data de abertura ainda pode mudar: só enquanto o enquadramento nunca "
                        + "mudou e não há notas fiscais")
        boolean openingDateEditable,
        @Schema(requiredMode = REQUIRED) RevenueBasis revenueBasis,
        @Schema(requiredMode = REQUIRED, description = "Fração: 0.28 é 28%", example = "0.28")
        BigDecimal fatorRTarget,
        @Schema(requiredMode = REQUIRED) BigDecimal openingBalance,
        @Schema(requiredMode = REQUIRED) LocalDate openingBalanceDate,
        @Schema(requiredMode = REQUIRED, description = "O enquadramento que vale no mês corrente")
        CompanyRegimeResponse currentRegime,
        @Schema(requiredMode = REQUIRED) boolean archived,
        @Schema(requiredMode = REQUIRED) Instant createdAt
) {
    public static CompanyResponse from(CompanyDetails details) {
        Company company = details.company();
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getEmoji(),
                company.getCnpj(),
                company.getOpeningDate(),
                details.openingDateEditable(),
                company.getRevenueBasis(),
                company.getFatorRTarget(),
                company.getOpeningBalance(),
                company.getOpeningBalanceDate(),
                CompanyRegimeResponse.from(details.currentRegime()),
                company.isArchived(),
                company.getCreatedAt());
    }
}
