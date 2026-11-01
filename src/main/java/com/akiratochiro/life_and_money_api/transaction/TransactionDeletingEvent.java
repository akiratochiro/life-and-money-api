package com.akiratochiro.life_and_money_api.transaction;

/**
 * Publicado logo antes de uma transação ser apagada, dentro da mesma transação do banco, para que
 * quem aponta para ela solte a referência antes do DELETE.
 *
 * <p>É o caso do pagamento de uma conta: {@code bill} depende de {@code transaction} para criar a
 * transação do pagamento, então {@code transaction} não pode chamar {@code bill} sem criar um
 * ciclo. Com o evento, excluir pelo extrato uma transação que pagou uma conta devolve a ocorrência
 * para pendente, em vez de esbarrar na chave estrangeira.
 */
public record TransactionDeletingEvent(Long userId, Long transactionId) {
}
