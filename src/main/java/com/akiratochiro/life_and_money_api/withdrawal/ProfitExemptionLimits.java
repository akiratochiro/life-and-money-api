package com.akiratochiro.life_and_money_api.withdrawal;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

/**
 * O limite estimado de lucro isento sem contabilidade, do começo do ano até o mês. Quem calcula é o
 * {@code tax}, que precisa do DAS; e o {@code tax} já depende de {@code withdrawal} (o pró-labore do
 * DARF e do Fator R). Esta interface inverte a dependência, como o {@code OpeningDateLock}.
 */
public interface ProfitExemptionLimits {

    /** Vazio quando o app não consegue estimar: regime fora do Simples e do MEI, ou mês que ele não calcula. */
    Optional<BigDecimal> limitUntil(Long userId, Long companyId, YearMonth month);
}
