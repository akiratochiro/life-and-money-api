package com.akiratochiro.life_and_money_api.budget;

import com.akiratochiro.life_and_money_api.category.CategoryArchivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Uma categoria arquivada não recebe mais lançamentos, então manter um limite ativo para ela só
 * polui o orçamento com uma linha que nunca sai de zero. Ao arquivar, o orçamento é encerrado.
 *
 * <p>O listener é síncrono e roda dentro da transação de quem publicou o evento: se encerrar o
 * orçamento falhar, o arquivamento não é gravado. Os meses anteriores continuam intactos, com o
 * limite que valia na época.
 */
@Component
class ArchivedCategoryListener {

    private static final Logger log = LoggerFactory.getLogger(ArchivedCategoryListener.class);

    private final BudgetService budgetService;

    ArchivedCategoryListener(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @EventListener
    void onCategoryArchived(CategoryArchivedEvent event) {
        log.debug("Encerrando o orçamento da categoria arquivada {}", event.categoryId());
        budgetService.endBudgetIfActive(event.userId(), event.categoryId());
    }
}
