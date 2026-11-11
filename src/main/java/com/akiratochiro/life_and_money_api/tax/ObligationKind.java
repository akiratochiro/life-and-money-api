package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.TaxRegime;

/**
 * As obrigações do sistema e em que regime cada uma vale. Lucro Presumido e Real não têm cálculo
 * automático: os impostos deles entram como obrigações personalizadas ({@link #CUSTOM}).
 */
public enum ObligationKind {
    DAS,
    DAS_MEI,
    DARF_PREVIDENCIARIO,
    DEFIS,
    DASN_SIMEI,
    CUSTOM;

    /** Se a obrigação do sistema vale num mês com este regime. */
    boolean appliesTo(TaxRegime regime) {
        return switch (this) {
            case DAS, DARF_PREVIDENCIARIO, DEFIS -> regime == TaxRegime.SIMPLES_NACIONAL;
            case DAS_MEI, DASN_SIMEI -> regime == TaxRegime.SIMEI;
            case CUSTOM -> true;
        };
    }

    /** As declarações não têm valor a pagar: são marcadas como entregues. */
    boolean isDeclaration() {
        return this == DEFIS || this == DASN_SIMEI;
    }
}
