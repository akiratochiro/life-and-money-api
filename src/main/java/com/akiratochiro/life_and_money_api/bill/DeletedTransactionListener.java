package com.akiratochiro.life_and_money_api.bill;

import com.akiratochiro.life_and_money_api.transaction.TransactionDeletingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Excluir pelo extrato a transação que pagou uma conta desfaz o pagamento: a ocorrência volta a
 * pendente (ou atrasada). Síncrono e na mesma transação, como o listener do orçamento, porque o
 * pagamento precisa sumir antes do DELETE da transação, senão a chave estrangeira o barra.
 */
@Component
class DeletedTransactionListener {

    private final BillService billService;

    DeletedTransactionListener(BillService billService) {
        this.billService = billService;
    }

    @EventListener
    void onTransactionDeleting(TransactionDeletingEvent event) {
        billService.releasePayment(event.userId(), event.transactionId());
    }
}
