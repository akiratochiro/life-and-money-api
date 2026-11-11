package com.akiratochiro.life_and_money_api.withdrawal;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * O pró-labore de um sócio numa competência, como as outras features precisam dele (DARF, Fator R).
 *
 * @param registered verdadeiro quando é um pró-labore pago e registrado; falso quando é o planejado
 */
public record ProLaboreSource(Long partnerId, YearMonth competence, BigDecimal gross, BigDecimal inss, BigDecimal irrf,
                              boolean registered) {
}
