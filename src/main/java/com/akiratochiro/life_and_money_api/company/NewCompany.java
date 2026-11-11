package com.akiratochiro.life_and_money_api.company;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os dados de uma empresa nova, já sem as anotações de HTTP. Os opcionais chegam nulos e ganham
 * padrão no service: meta do Fator R de 28%, saldo inicial zero na data de hoje e o nome do
 * usuário como sócio.
 */
record NewCompany(
        String name,
        String emoji,
        String cnpj,
        LocalDate openingDate,
        LegalNature legalNature,
        TaxRegime taxRegime,
        AnnexConfig annexConfig,
        MeiActivity meiActivity,
        BigDecimal fatorRTarget,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate,
        String partnerName
) {
}
