package com.akiratochiro.life_and_money_api.category;

/**
 * Publicado quando uma categoria é arquivada, para que outras features reajam ao fato sem que
 * o pacote {@code category} precise conhecê-las.
 *
 * <p>O caminho natural seria a CategoryService chamar a BudgetService, mas a BudgetService já
 * depende da CategoryService para validar a categoria: a chamada direta criaria um ciclo entre
 * os dois pacotes. Com o evento, {@code category} só anuncia o que aconteceu e quem se interessa
 * escuta — a mesma ideia do {@code UserRegisteredEvent}, que cria as categorias padrão.
 */
public record CategoryArchivedEvent(Long userId, Long categoryId) {
}
