package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.category.CategoryArchivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Arquivar uma categoria encerra as contas dela, como já encerra o orçamento: sem isso, as
 * próximas ocorrências ficariam no calendário sem poder ser pagas. Síncrono e na transação de quem
 * publicou: se encerrar falhar, o arquivamento também não é gravado.
 */
@Component
class ArchivedCategoryBillsListener {

    private final BillService billService;

    ArchivedCategoryBillsListener(BillService billService) {
        this.billService = billService;
    }

    @EventListener
    void onCategoryArchived(CategoryArchivedEvent event) {
        billService.endBillsOfCategory(event.userId(), event.categoryId());
    }
}
