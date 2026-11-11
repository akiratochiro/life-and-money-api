package com.akiratochiro.life_and_money_api.company;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edição parcial: o que vier nulo fica como está. O CNPJ é a exceção que precisa de um terceiro
 * estado, porque é opcional: texto vazio remove o CNPJ, nulo mantém o atual.
 */
record CompanyChanges(
        String name,
        String emoji,
        String cnpj,
        LocalDate openingDate,
        BigDecimal fatorRTarget,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate
) {
}
