package com.akiratochiro.life_and_money_api.support;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportRequest(
        @Schema(description = "Opcional; sem ele, \"Mensagem pelo app\"")
        @Size(max = 100, message = "O assunto deve ter no máximo 100 caracteres.")
        String subject,

        @NotBlank(message = "Escreva a sua mensagem.")
        @Size(min = 10, max = 3000, message = "A mensagem deve ter entre 10 e 3.000 caracteres.")
        String message
) {
}
