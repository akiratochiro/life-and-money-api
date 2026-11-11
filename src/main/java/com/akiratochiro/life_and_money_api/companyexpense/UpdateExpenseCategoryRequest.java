package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edição parcial: o que não vier fica como está. */
public record UpdateExpenseCategoryRequest(
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "Informe o nome da categoria.")
        String name,
        CategoryColor color,
        CategoryIcon icon,
        Boolean countsAsPayroll
) {
}
