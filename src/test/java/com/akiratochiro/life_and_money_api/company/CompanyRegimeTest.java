package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyRegimeTest {

    private static CompanyRegime meiFrom(YearMonth month) {
        return new CompanyRegime(1L, 10L, LegalNature.MEI, TaxRegime.SIMEI, null, MeiActivity.SERVICOS, month);
    }

    @Test
    void anOpenRegimeIsValidFromItsStartOnwards() {
        CompanyRegime regime = meiFrom(YearMonth.of(2024, 3));

        assertThat(regime.isValidIn(YearMonth.of(2024, 2))).isFalse();
        assertThat(regime.isValidIn(YearMonth.of(2024, 3))).isTrue();
        assertThat(regime.isValidIn(YearMonth.of(2030, 1))).isTrue();
    }

    @Test
    void aClosedRegimeIsValidUpToItsLastMonthInclusive() {
        CompanyRegime regime = meiFrom(YearMonth.of(2024, 3));
        regime.close(YearMonth.of(2025, 2));

        assertThat(regime.isValidIn(YearMonth.of(2025, 2))).isTrue();
        assertThat(regime.isValidIn(YearMonth.of(2025, 3))).isFalse();
        assertThat(regime.isActive()).isFalse();
    }

    @Test
    void reopeningRemovesTheEnd() {
        CompanyRegime regime = meiFrom(YearMonth.of(2024, 3));
        regime.close(YearMonth.of(2025, 2));

        regime.reopen();

        assertThat(regime.isActive()).isTrue();
        assertThat(regime.isValidIn(YearMonth.of(2027, 1))).isTrue();
    }

    @Test
    void itCannotCloseBeforeItStartsNorTwice() {
        CompanyRegime regime = meiFrom(YearMonth.of(2024, 3));

        assertThatThrownBy(() -> regime.close(YearMonth.of(2024, 2))).isInstanceOf(IllegalArgumentException.class);
        regime.close(YearMonth.of(2024, 3));
        assertThatThrownBy(() -> regime.close(YearMonth.of(2024, 5))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sameAsComparesEveryPartOfTheRegime() {
        CompanyRegime regime = meiFrom(YearMonth.of(2024, 3));

        assertThat(regime.sameAs(LegalNature.MEI, TaxRegime.SIMEI, null, MeiActivity.SERVICOS)).isTrue();
        assertThat(regime.sameAs(LegalNature.MEI, TaxRegime.SIMEI, null, MeiActivity.AMBOS)).isFalse();
    }
}
