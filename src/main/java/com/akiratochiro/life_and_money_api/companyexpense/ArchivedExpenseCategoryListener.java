package com.akiratochiro.life_and_money_api.companyexpense;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Arquivar uma categoria encerra as despesas fixas dela: uma categoria arquivada não aceita despesa
 * nova, e os próximos meses ficariam pendentes sem poder ser pagos. Síncrono, na mesma transação.
 */
@Component
class ArchivedExpenseCategoryListener {

    private final CompanyExpenseService expenseService;

    ArchivedExpenseCategoryListener(CompanyExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @EventListener
    void onArchived(ExpenseCategoryArchivedEvent event) {
        expenseService.endRecurringOfCategory(event.categoryId());
    }
}
