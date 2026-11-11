package com.akiratochiro.life_and_money_api.companyexpense;

/** Uma categoria de despesa foi arquivada: as despesas fixas dela são encerradas. */
public record ExpenseCategoryArchivedEvent(Long userId, Long companyId, Long categoryId) {
}
