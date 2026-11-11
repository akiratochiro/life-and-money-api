package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ExpenseCategoryResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) CategoryColor color,
        @Schema(requiredMode = REQUIRED) CategoryIcon icon,
        @Schema(requiredMode = REQUIRED, description = "Entra na folha de salários do Fator R") boolean countsAsPayroll,
        @Schema(requiredMode = REQUIRED, description = "Do sistema (Impostos): não pode ser escolhida nem alterada")
        boolean system,
        @Schema(requiredMode = REQUIRED) boolean archived
) {
    public static ExpenseCategoryResponse from(CompanyExpenseCategory category) {
        return new ExpenseCategoryResponse(category.getId(), category.getName(), category.getColor(), category.getIcon(),
                category.countsAsPayroll(), category.isSystem(), category.isArchived());
    }
}
