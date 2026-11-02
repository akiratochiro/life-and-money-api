package com.akiratochiro.life_and_money_api.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Escolha o tipo da categoria.")
        CategoryType type,

        @Schema(description = "Opcional; sem ela, vale a cor padrão do tipo")
        CategoryColor color,

        @Schema(description = "Opcional; sem ele, vale o ícone padrão do tipo")
        CategoryIcon icon
) {
}
