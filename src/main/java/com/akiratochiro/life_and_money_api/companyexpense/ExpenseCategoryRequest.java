package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,
        @Schema(description = "Sem ela, CORAL") CategoryColor color,
        @Schema(description = "Sem ele, OTHER") CategoryIcon icon,
        @Schema(description = "Entra na folha de salários do Fator R; sem ele, não") Boolean countsAsPayroll
) {
}
