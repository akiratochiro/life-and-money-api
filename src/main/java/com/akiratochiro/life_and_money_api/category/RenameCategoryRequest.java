package com.akiratochiro.life_and_money_api.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameCategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name
) {
}
