package com.akiratochiro.life_and_money_api.taxengine;

import java.time.YearMonth;

/**
 * Qual conjunto de regras vale numa competência. As regras do Simples deste motor são as da LC
 * 123 com a redação da LC 155/2016 e da Res. CGSN 140/2018, em vigor desde 2018 até a competência
 * de 12/2026. A partir de 01/2027, a Res. CGSN 190/2026 e a LC 214/2025 (CBS e IBS) mudam o RBT12,
 * o Fator R no início de atividade, o ISS retido e o DAS-MEI; essas regras entram numa entrega
 * própria, lida no texto oficial (plano da Fase 4, item 1.1).
 */
public final class TaxRules {

    public static final YearMonth FIRST_UNSUPPORTED = YearMonth.of(2027, 1);

    private TaxRules() {
    }

    public static void requireSupported(YearMonth competence) {
        if (!competence.isBefore(FIRST_UNSUPPORTED)) {
            throw new TaxCalculationException("As regras do Simples Nacional e do MEI mudam em janeiro de 2027 (reforma "
                    + "tributária e Resolução CGSN 190/2026) e ainda não estão no app. Até lá, consulte o seu contador "
                    + "para as competências de 2027.");
        }
    }
}
