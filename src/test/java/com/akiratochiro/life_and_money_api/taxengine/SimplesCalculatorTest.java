package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os exemplos feitos à mão do plano da Fase 4, seção 3. */
class SimplesCalculatorTest {

    private static final TaxTable T = Tables2026.table();
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void annexIIIBracket2() {
        // (300.000 × 11,2% − 9.360) ÷ 300.000 = 8,08%; 25.000 × 8,08% = 2.020,00
        var result = SimplesCalculator.das(Annex.III, d("300000"), d("25000"), ZERO, T);

        assertThat(result.bracket()).isEqualTo(2);
        assertThat(result.effectiveRate()).isEqualByComparingTo("0.0808");
        assertThat(result.amount()).isEqualByComparingTo("2020.00");
    }

    @Test
    void annexVBracket2() {
        // (300.000 × 18% − 4.500) ÷ 300.000 = 16,5%; 25.000 × 16,5% = 4.125,00
        var result = SimplesCalculator.das(Annex.V, d("300000"), d("25000"), ZERO, T);

        assertThat(result.effectiveRate()).isEqualByComparingTo("0.165");
        assertThat(result.amount()).isEqualByComparingTo("4125.00");
    }

    @Test
    void withheldIssLeavesTheDas() {
        // Todo o ISS retido: 2.020 × (1 − 32%) = 1.373,60
        assertThat(SimplesCalculator.das(Annex.III, d("300000"), d("25000"), d("25000"), T).amount())
                .isEqualByComparingTo("1373.60");
        // Metade retida: 12.500 × 8,08% + 12.500 × 8,08% × 68% = 1.010 + 686,80 = 1.696,80
        assertThat(SimplesCalculator.das(Annex.III, d("300000"), d("25000"), d("12500"), T).amount())
                .isEqualByComparingTo("1696.80");
    }

    /**
     * Faixa 5 do Anexo III: com RBT12 de 3.000.000, a efetiva é (3.000.000 × 21% − 125.640) ÷ 3.000.000
     * = 16,812%, e 33,5% disso seria 5,632%: o ISS fica limitado a 5%.
     */
    @Test
    void theIssShareIsCappedAtFivePercent() {
        var result = SimplesCalculator.das(Annex.III, d("3000000"), d("100000"), d("100000"), T);

        assertThat(result.effectiveRate()).isEqualByComparingTo("0.16812");
        assertThat(result.issRate()).isEqualByComparingTo("0.05");
        assertThat(result.amount()).isEqualByComparingTo("11812.00"); // 100.000 × (16,812% − 5%)
    }

    @Test
    void theUpperLimitOfABracketBelongsToIt() {
        assertThat(SimplesCalculator.das(Annex.III, d("180000.00"), d("1000"), ZERO, T).bracket()).isEqualTo(1);
        assertThat(SimplesCalculator.das(Annex.III, d("180000.01"), d("1000"), ZERO, T).bracket()).isEqualTo(2);
    }

    @Test
    void withoutRevenueInTheLast12MonthsTheFirstNominalRateApplies() {
        assertThat(SimplesCalculator.das(Annex.III, ZERO, d("1000"), ZERO, T).effectiveRate()).isEqualByComparingTo("0.06");
    }

    @Test
    void aboveTheSublimitAndAboveTheCeilingAreNotCalculated() {
        assertThatThrownBy(() -> SimplesCalculator.das(Annex.III, d("3600000.01"), d("1000"), ZERO, T))
                .isInstanceOf(TaxCalculationException.class)
                .hasMessage("Acima de R$ 3.600.000,00 nos últimos 12 meses, o ISS sai do DAS e o cálculo muda. "
                        + "O app ainda não calcula esse caso: consulte o seu contador.");
        assertThatThrownBy(() -> SimplesCalculator.das(Annex.V, d("4800000.01"), d("1000"), ZERO, T))
                .hasMessageContaining("passa do teto do Simples Nacional");
    }

    /** As frações da repartição de cada faixa somam 100%: um erro de digitação na semente apareceria aqui. */
    @Test
    void everyBracketSharesAddUpToOne() {
        for (SimplesBracket b : T.simplesBrackets()) {
            BigDecimal total = b.irpjShare().add(b.csllShare()).add(b.cofinsShare()).add(b.pisShare()).add(b.cppShare())
                    .add(b.issShare());
            assertThat(total).as(b.annex() + " faixa " + b.bracket()).isEqualByComparingTo("1");
        }
    }
}
