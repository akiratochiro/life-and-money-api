package com.akiratochiro.life_and_money_api.account;

import com.akiratochiro.life_and_money_api.bill.BillExport;
import com.akiratochiro.life_and_money_api.budget.BudgetItemResponse;
import com.akiratochiro.life_and_money_api.category.CategoryResponse;
import com.akiratochiro.life_and_money_api.company.CompanyExport;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseCategoryExportRow;
import com.akiratochiro.life_and_money_api.companyexpense.ExpenseResponse;
import com.akiratochiro.life_and_money_api.companyexpense.RecurringExpenseResponse;
import com.akiratochiro.life_and_money_api.goal.SavingsGoalResponse;
import com.akiratochiro.life_and_money_api.invoice.InvoiceExport;
import com.akiratochiro.life_and_money_api.transaction.TransactionResponse;
import com.akiratochiro.life_and_money_api.user.UserResponse;
import com.akiratochiro.life_and_money_api.withdrawal.ProLaborePlanResponse;
import com.akiratochiro.life_and_money_api.withdrawal.WithdrawalResponse;
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
        @Schema(requiredMode = REQUIRED) List<SavingsGoalResponse> goals,
        @Schema(requiredMode = REQUIRED, description = "Contas a pagar e a receber, com todo o histórico")
        List<BillExport> bills,
        @Schema(requiredMode = REQUIRED, description = "Empresas, arquivadas inclusive, com o histórico de enquadramento e os sócios")
        List<CompanyExport> companies,
        @Schema(requiredMode = REQUIRED, description = "Os dados das notas fiscais; os arquivos originais são baixados na tela")
        List<InvoiceExport> invoices,
        @Schema(requiredMode = REQUIRED, description = "Categorias de despesa das empresas, arquivadas inclusive")
        List<ExpenseCategoryExportRow> expenseCategories,
        @Schema(requiredMode = REQUIRED, description = "Despesas fixas das empresas, com o histórico dos termos")
        List<RecurringExpenseResponse> recurringExpenses,
        @Schema(requiredMode = REQUIRED, description = "Despesas pagas das empresas (avulsas e pagamentos de fixas)")
        List<ExpenseResponse> companyExpenses,
        @Schema(requiredMode = REQUIRED, description = "Pró-labores planejados, com o histórico")
        List<ProLaborePlanResponse> proLaborePlans,
        @Schema(requiredMode = REQUIRED, description = "Retiradas dos sócios (pró-labores pagos)")
        List<WithdrawalResponse> withdrawals
) {
}
