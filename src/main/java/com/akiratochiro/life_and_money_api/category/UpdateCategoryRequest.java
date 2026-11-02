package com.akiratochiro.life_and_money_api.category;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edição parcial: o que vier, muda; o que não vier, fica como está. */
public record UpdateCategoryRequest(
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "Informe o nome da categoria.")
        @Schema(description = "Opcional; ausente para manter o nome")
        String name,

        @Schema(description = "Opcional; ausente para manter a cor")
        CategoryColor color,

        @Schema(description = "Opcional; ausente para manter o ícone")
        CategoryIcon icon
) {

    @JsonIgnore
    @AssertTrue(message = "Informe o que mudar: nome, cor ou ícone.")
    public boolean isAnyChange() {
        return name != null || color != null || icon != null;
    }
}
