package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProLaboreCalculatorTest {

    private static final TaxTable T = Tables2026.table();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /**
     * Exemplo 4 oficial da Receita para a Lei 15.270/2025 (salário de R$ 6.000, com o INSS do empregado
     * de R$ 649,60): base R$ 5.350,40, IR da tabela R$ 562,63, redução R$ 179,75, IRRF R$ 382,88.
     */
    @Test
    void reproducesTheOfficialExampleOfTheReceita() {
        var irrf = ProLaboreCalculator.irrf(d("6000"), d("649.60"), T);

        assertThat(irrf.base()).isEqualByComparingTo("5350.40");
        assertThat(irrf.beforeReduction()).isEqualByComparingTo("562.63");
        assertThat(irrf.reduction()).isEqualByComparingTo("179.75");
        assertThat(irrf.amount()).isEqualByComparingTo("382.88");
    }

    /**
     * Pró-labore de R$ 7.000: INSS 11% = 770,00; base 6.230,00; IR 804,52; redução 978,62 − 0,133145 ×
     * 7.000 = 46,605, arredondada para 46,61; IRRF 757,91; líquido 5.472,09.
     */
    @Test
    void aProLaboreOf7000() {
        var result = ProLaboreCalculator.of(d("7000"), T);

        assertThat(result.inss()).isEqualByComparingTo("770.00");
        assertThat(result.irrfBase()).isEqualByComparingTo("6230.00");
        assertThat(result.irrfBeforeReduction()).isEqualByComparingTo("804.52");
        assertThat(result.reduction()).isEqualByComparingTo("46.61");
        assertThat(result.irrf()).isEqualByComparingTo("757.91");
        assertThat(result.net()).isEqualByComparingTo("5472.09");
    }

    /** INSS é 11% do bruto, não a tabela progressiva do empregado, e para no teto. */
    @Test
    void theInssIsElevenPercentCappedAtTheCeiling() {
        assertThat(ProLaboreCalculator.of(d("1621"), T).inss()).isEqualByComparingTo("178.31");
        assertThat(ProLaboreCalculator.of(d("8475.55"), T).inss()).isEqualByComparingTo("932.31");
        assertThat(ProLaboreCalculator.of(d("20000"), T).inss()).isEqualByComparingTo("932.31");
    }

    /** Até R$ 5.000 de bruto, o IR fica zero; em R$ 5.000 exatos, o IR da tabela é a redução máxima (312,89). */
    @ParameterizedTest
    @CsvSource({"1621, 0", "3000, 0", "5000, 0"})
    void upToFiveThousandTheIncomeTaxIsZero(String gross, String irrf) {
        assertThat(ProLaboreCalculator.of(d(gross), T).irrf()).isEqualByComparingTo(irrf);
    }

    @Test
    void atExactlyFiveThousandTheReductionIsTheLegalMaximum() {
        var result = ProLaboreCalculator.of(d("5000"), T);

        // base 5.000 − 607,20 (o desconto simplificado é maior que os 550 de INSS) = 4.392,80; IR 312,89
        assertThat(result.irrfBeforeReduction()).isEqualByComparingTo("312.89");
        assertThat(result.reduction()).isEqualByComparingTo("312.89");
    }

    /** Acima de R$ 7.350, nenhuma redução: R$ 10.000 → base 9.067,69; IR 9.067,69 × 27,5% − 908,73 = 1.584,88475 → 1.584,88. */
    @Test
    void aboveTheReductionLimitTheFullTableApplies() {
        var result = ProLaboreCalculator.of(d("10000"), T);

        assertThat(result.inss()).isEqualByComparingTo("932.31");
        assertThat(result.reduction()).isEqualByComparingTo("0");
        assertThat(result.irrf()).isEqualByComparingTo("1584.88");
    }

    @Test
    void belowTheMinimumWageItWarns() {
        assertThat(ProLaboreCalculator.of(d("1000"), T).belowMinimumWage()).isTrue();
        assertThat(ProLaboreCalculator.of(d("1621"), T).belowMinimumWage()).isFalse();
    }
}
