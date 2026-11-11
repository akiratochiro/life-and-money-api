package com.akiratochiro.life_and_money_api.companyexpense;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Uma categoria de despesa no export da conta, com a empresa dela. */
public record ExpenseCategoryExportRow(
        @Schema(requiredMode = REQUIRED) Long companyId,
        @Schema(requiredMode = REQUIRED) ExpenseCategoryResponse category
) {
    public static ExpenseCategoryExportRow from(CompanyExpenseCategory category) {
        return new ExpenseCategoryExportRow(category.getCompanyId(), ExpenseCategoryResponse.from(category));
    }
}
