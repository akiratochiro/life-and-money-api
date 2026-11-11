package com.akiratochiro.life_and_money_api.company;

import java.time.YearMonth;

/** Um enquadramento novo, valendo a partir de {@code validFrom}. */
record RegimeChange(
        YearMonth validFrom,
        LegalNature legalNature,
        TaxRegime taxRegime,
        AnnexConfig annexConfig,
        MeiActivity meiActivity
) {
}
