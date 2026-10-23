package com.akiratochiro.life_and_money_api.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Escolha o tipo da categoria.")
        CategoryType type
) {
}
