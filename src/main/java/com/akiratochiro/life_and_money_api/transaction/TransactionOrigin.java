package com.akiratochiro.life_and_money_api.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * De onde veio uma transação que outra feature criou: hoje, uma retirada da empresa (Fase 6). Valor e
 * data dela são mudados na origem; aqui, só categoria e descrição.
 */
public record TransactionOrigin(
        @Schema(requiredMode = REQUIRED) Kind kind,
        @Schema(requiredMode = REQUIRED, example = "Pró-labore 10/2026 · Estúdio Ana") String label,
        @Schema(requiredMode = REQUIRED) Long companyId,
        @Schema(requiredMode = REQUIRED) Long withdrawalId,
        @Schema(requiredMode = REQUIRED, description = "Falso com a empresa arquivada: a origem não pode mais ser editada nem excluída")
        boolean editable
) {
    public enum Kind { WITHDRAWAL }
}
