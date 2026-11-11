package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.companyexpense.CompanyExpenseDeletingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Apagar a despesa de um imposto pago na aba Despesas desfaz o pagamento, como apagar a transação
 * de uma conta paga desfaz o pagamento dela (decisão 10). Síncrono, antes do DELETE da despesa.
 */
@Component
class ReleasedTaxExpenseListener {

    private final TaxService taxService;

    ReleasedTaxExpenseListener(TaxService taxService) {
        this.taxService = taxService;
    }

    @EventListener
    void onExpenseDeleting(CompanyExpenseDeletingEvent event) {
        taxService.releaseExpense(event.expenseId());
    }
}
