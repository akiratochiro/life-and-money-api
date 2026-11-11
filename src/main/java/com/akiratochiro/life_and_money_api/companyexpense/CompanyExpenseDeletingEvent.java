package com.akiratochiro.life_and_money_api.companyexpense;

/**
 * Uma despesa da empresa vai ser apagada. Publicado antes do DELETE, como o
 * {@code TransactionDeletingEvent} (decisão 10): quem aponta para ela (o pagamento de um imposto)
 * se solta enquanto a linha ainda existe.
 */
public record CompanyExpenseDeletingEvent(Long userId, Long companyId, Long expenseId) {
}
