package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma empresa no export da conta: os dados cadastrais, todo o histórico de enquadramento e os sócios. */
public record CompanyExport(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) String emoji,
        String cnpj,
        @Schema(requiredMode = REQUIRED) LocalDate openingDate,
        @Schema(requiredMode = REQUIRED) RevenueBasis revenueBasis,
        @Schema(requiredMode = REQUIRED) BigDecimal fatorRTarget,
        @Schema(requiredMode = REQUIRED) BigDecimal openingBalance,
        @Schema(requiredMode = REQUIRED) LocalDate openingBalanceDate,
        @Schema(requiredMode = REQUIRED) boolean archived,
        @Schema(requiredMode = REQUIRED) List<CompanyRegimeResponse> regimes,
        @Schema(requiredMode = REQUIRED) List<PartnerResponse> partners
) {
    static CompanyExport from(Company company, List<CompanyRegime> regimes, List<Partner> partners) {
        return new CompanyExport(company.getId(), company.getName(), company.getEmoji(), company.getCnpj(),
                company.getOpeningDate(), company.getRevenueBasis(), company.getFatorRTarget(),
                company.getOpeningBalance(), company.getOpeningBalanceDate(), company.isArchived(),
                regimes.stream().map(CompanyRegimeResponse::from).toList(),
                partners.stream().map(PartnerResponse::from).toList());
    }
}
