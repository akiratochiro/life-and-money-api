package com.akiratochiro.life_and_money_api.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerRequest(
        @NotBlank(message = "Informe o nome do sócio.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name
) {
}
