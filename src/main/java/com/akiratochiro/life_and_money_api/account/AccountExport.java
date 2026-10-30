package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.budget.BudgetItemResponse;
import com.akiratochiro.life_and_money_api.category.CategoryResponse;
import com.akiratochiro.life_and_money_api.goal.SavingsGoalResponse;
import com.akiratochiro.life_and_money_api.transaction.TransactionResponse;
import com.akiratochiro.life_and_money_api.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Tudo o que é do usuário, no mesmo formato das respostas da API. Ficam de fora o hash da senha
 * e os tokens de sessão e de redefinição: são credenciais, não dados da pessoa.
 */
public record AccountExport(
        @Schema(requiredMode = REQUIRED) Instant exportedAt,
        @Schema(requiredMode = REQUIRED) UserResponse profile,
        @Schema(requiredMode = REQUIRED, description = "Inclusive as arquivadas") List<CategoryResponse> categories,
        @Schema(requiredMode = REQUIRED) List<TransactionResponse> transactions,
        @Schema(requiredMode = REQUIRED, description = "Todo o histórico, inclusive itens encerrados")
        List<BudgetItemResponse> budgetItems,
        @Schema(requiredMode = REQUIRED) List<SavingsGoalResponse> goals
) {
}
