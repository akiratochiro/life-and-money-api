package com.akiratochiro.life_and_money_api.withdrawal;

import com.akiratochiro.life_and_money_api.transaction.TransactionDeletingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Excluir no extrato a transação lançada por uma retirada solta a ligação, antes do DELETE (a FK a
 * barraria), como o pagamento de uma conta. Excluir as duas é outro pedido: a exclusão da retirada
 * com {@code deletePersonal}.
 */
@Component
class DeletedPersonalTransactionListener {

    private final WithdrawalService service;

    DeletedPersonalTransactionListener(WithdrawalService service) {
        this.service = service;
    }

    @EventListener
    void onTransactionDeleting(TransactionDeletingEvent event) {
        service.releasePersonal(event.transactionId());
    }
}
