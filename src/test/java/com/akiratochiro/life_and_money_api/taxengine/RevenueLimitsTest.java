package com.akiratochiro.life_and_money_api.taxengine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Os exemplos feitos à mão do plano da Fase 10, com os limites de 2026. */
class RevenueLimitsTest {

    private static final TaxTable T = Tables2026.table();
    private static final YearMonth JANUARY = YearMonth.of(2026, 1);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** O mesmo valor em cada mês, de {@code from} a {@code to}. */
    private static Map<YearMonth, BigDecimal> monthly(String amount, YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> months = new HashMap<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            months.put(m, d(amount));
        }
        return months;
    }

    private static RevenueLimits.Result closed(RevenueLimits.Regime regime, YearMonth start, Map<YearMonth, BigDecimal> actual) {
        return RevenueLimits.of(2026, regime, start, actual, null, Map.of(), T);
    }

    /**
     * MEI aberto em 20/09/2026: setembro conta inteiro, 4 meses × R$ 6.750 = R$ 27.000 (tolerância R$ 32.400).
     * Notas de 5.000, 8.000 e 8.450 = 21.450, 79% (atenção). Projetando 8.450 em novembro e dezembro, o
     * acumulado passa de 27.000 em dezembro (29.900).
     */
    @Test
    void aMeiInItsOpeningYearHasAProportionalLimitAndAProjection() {
        YearMonth start = YearMonth.of(2026, 9);
        Map<YearMonth, BigDecimal> actual = Map.of(start, d("5000"), YearMonth.of(2026, 10), d("8000"), YearMonth.of(2026, 11), d("8450"));
        Map<YearMonth, BigDecimal> projected = Map.of(YearMonth.of(2026, 11), d("8450"), YearMonth.of(2026, 12), d("8450"));

        var result = RevenueLimits.of(2026, RevenueLimits.Regime.MEI, start, actual, YearMonth.of(2026, 11), projected, T);

        assertThat(result.openingYear()).isTrue();
        assertThat(result.activityMonths()).isEqualTo(4);
        assertThat(result.revenue()).isEqualByComparingTo("21450");
        assertThat(result.projectionTotal()).isEqualByComparingTo("29900");
        var mei = result.limits().getFirst();
        assertThat(mei.amount()).isEqualByComparingTo("27000.00");
        assertThat(mei.toleranceAmount()).isEqualByComparingTo("32400.00");
        assertThat(mei.ratio()).isEqualByComparingTo("0.7944");
        assertThat(mei.level()).isEqualTo(RevenueLimits.Level.WARNING);
        assertThat(mei.projectedIn()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(result.alert()).isEqualTo(RevenueLimits.Kind.MEI_ANNUAL);
    }

    /** 7.000 por mês de janeiro a novembro: 77.000 de 81.000, 95% (perto). */
    @Test
    void aMeiAboveNinetyPercentIsNear() {
        var mei = closed(RevenueLimits.Regime.MEI, YearMonth.of(2024, 1), monthly("7000", JANUARY, YearMonth.of(2026, 11)))
                .limits().getFirst();

        assertThat(mei.ratio()).isEqualByComparingTo("0.9506");
        assertThat(mei.level()).isEqualTo(RevenueLimits.Level.NEAR);
        assertThat(mei.exceeded()).isFalse();
    }

    /** O ano todo a 7.000: 84.000, passou em dezembro, dentro dos 20% (97.200): sai do MEI em janeiro. */
    @Test
    void aMeiWithinTheToleranceLeavesNextYear() {
        var mei = closed(RevenueLimits.Regime.MEI, YearMonth.of(2024, 1), monthly("7000", JANUARY, YearMonth.of(2026, 12)))
                .limits().getFirst();

        assertThat(mei.level()).isEqualTo(RevenueLimits.Level.EXCEEDED);
        assertThat(mei.exceededIn()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(mei.effect()).isEqualTo(RevenueLimits.Effect.MEI_LEAVES_NEXT_YEAR);
        assertThat(mei.effectiveFrom()).isEqualTo(YearMonth.of(2027, 1));
    }

    /**
     * 10.000 por mês: passa de 81.000 em setembro (90.000) e de 97.200 em outubro (100.000). O
     * desenquadramento retroage a janeiro; no ano de abertura (julho, limite de 40.500), à abertura.
     */
    @Test
    void aMeiBeyondTheToleranceIsRetroactive() {
        var mei = closed(RevenueLimits.Regime.MEI, YearMonth.of(2024, 1), monthly("10000", JANUARY, YearMonth.of(2026, 12)))
                .limits().getFirst();
        assertThat(mei.exceededIn()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(mei.toleranceExceededIn()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(mei.level()).isEqualTo(RevenueLimits.Level.EXCEEDED_TOLERANCE);
        assertThat(mei.effect()).isEqualTo(RevenueLimits.Effect.MEI_RETROACTIVE);
        assertThat(mei.effectiveFrom()).isEqualTo(JANUARY);

        YearMonth july = YearMonth.of(2026, 7);
        var opening = closed(RevenueLimits.Regime.MEI, july, monthly("10000", july, YearMonth.of(2026, 12))).limits().getFirst();
        assertThat(opening.amount()).isEqualByComparingTo("40500.00");
        assertThat(opening.effectiveFrom()).isEqualTo(july);
    }

    /**
     * Simples a 60.000 por mês: junho fecha em 360.000 (igual, não passou), julho em 420.000: vira EPP em
     * janeiro. O sublimite fica em 20% e o teto em 15%: o alerta é o da EPP.
     */
    @Test
    void theSimplesBecomesEppNextYear() {
        var result = closed(RevenueLimits.Regime.SIMPLES, YearMonth.of(2024, 1), monthly("60000", JANUARY, YearMonth.of(2026, 12)));

        var epp = result.limits().get(0);
        assertThat(epp.kind()).isEqualTo(RevenueLimits.Kind.SIMPLES_EPP);
        assertThat(epp.exceededIn()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(epp.toleranceAmount()).isNull();
        assertThat(epp.effect()).isEqualTo(RevenueLimits.Effect.BECOMES_EPP_NEXT_YEAR);
        assertThat(result.limits().get(1).level()).isEqualTo(RevenueLimits.Level.NONE);
        assertThat(result.alert()).isEqualTo(RevenueLimits.Kind.SIMPLES_EPP);
    }

    /**
     * 400.000 por mês: passa do sublimite em outubro (4,0 mi) e dos 20% (4,32 mi) em novembro (4,4 mi): o
     * ISS sai do DAS em dezembro. O teto fecha em 4,8 mi, igual ao limite: perto, e é o alerta.
     */
    @Test
    void theSublimitBeyondTwentyPercentTakesEffectNextMonth() {
        var result = closed(RevenueLimits.Regime.SIMPLES, YearMonth.of(2024, 1), monthly("400000", JANUARY, YearMonth.of(2026, 12)));

        var sublimit = result.limits().get(1);
        assertThat(sublimit.exceededIn()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(sublimit.toleranceExceededIn()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(sublimit.effect()).isEqualTo(RevenueLimits.Effect.ISS_OUT_NEXT_MONTH);
        assertThat(sublimit.effectiveFrom()).isEqualTo(YearMonth.of(2026, 12));
        var ceiling = result.limits().get(2);
        assertThat(ceiling.ratio()).isEqualByComparingTo("1.0000");
        assertThat(ceiling.level()).isEqualTo(RevenueLimits.Level.NEAR);
        assertThat(result.alert()).isEqualTo(RevenueLimits.Kind.SIMPLES_CEILING);
    }

    /**
     * Simples aberto em outubro: 3 meses, limites de 90.000, 900.000 e 1,2 mi. A 600.000 por mês, o
     * sublimite passa em novembro e o teto passa dos 20% (1,44 mi) em dezembro: os dois retroagem a outubro.
     */
    @Test
    void theSimplesOpeningYearIsProportionalAndRetroactive() {
        YearMonth october = YearMonth.of(2026, 10);
        var result = closed(RevenueLimits.Regime.SIMPLES, october, monthly("600000", october, YearMonth.of(2026, 12)));

        assertThat(result.limits()).extracting(RevenueLimits.Limit::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("90000"), d("900000"), d("1200000"));
        assertThat(result.limits().get(1).effect()).isEqualTo(RevenueLimits.Effect.ISS_OUT_RETROACTIVE);
        assertThat(result.limits().get(1).effectiveFrom()).isEqualTo(october);
        assertThat(result.limits().get(2).toleranceExceededIn()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(result.limits().get(2).effect()).isEqualTo(RevenueLimits.Effect.EXCLUDED_RETROACTIVE);
        assertThat(result.limits().get(2).effectiveFrom()).isEqualTo(october);
    }

    /**
     * Ano de abertura (outubro, sublimite de 900.000 e teto de 1,2 mi) com 300 + 300 + 350 mil = 950.000: o
     * sublimite passou em dezembro em menos de 20% e ainda assim retroage a outubro, porque o §12 só alcança o
     * §10 (o teto), não o §11 (o sublimite). Sem margem, o sublimite não tem valor de tolerância.
     */
    @Test
    void inTheOpeningYearAnySublimitExcessIsRetroactive() {
        YearMonth october = YearMonth.of(2026, 10);
        var sublimit = closed(RevenueLimits.Regime.SIMPLES, october,
                Map.of(october, d("300000"), YearMonth.of(2026, 11), d("300000"), YearMonth.of(2026, 12), d("350000")))
                .limits().get(1);

        assertThat(sublimit.toleranceAmount()).isNull();
        assertThat(sublimit.exceededIn()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(sublimit.level()).isEqualTo(RevenueLimits.Level.EXCEEDED);
        assertThat(sublimit.effect()).isEqualTo(RevenueLimits.Effect.ISS_OUT_RETROACTIVE);
        assertThat(sublimit.effectiveFrom()).isEqualTo(october);
    }

    /** Ano de abertura com 400 + 400 + 500 mil = 1,3 mi: o teto (1,2 mi) passou em até 20% (1,44 mi), e o §12 manda para janeiro. */
    @Test
    void inTheOpeningYearTheCeilingKeepsItsTwentyPercent() {
        YearMonth october = YearMonth.of(2026, 10);
        var ceiling = closed(RevenueLimits.Regime.SIMPLES, october,
                Map.of(october, d("400000"), YearMonth.of(2026, 11), d("400000"), YearMonth.of(2026, 12), d("500000")))
                .limits().get(2);

        assertThat(ceiling.level()).isEqualTo(RevenueLimits.Level.EXCEEDED);
        assertThat(ceiling.effect()).isEqualTo(RevenueLimits.Effect.EXCLUDED_NEXT_YEAR);
        assertThat(ceiling.effectiveFrom()).isEqualTo(YearMonth.of(2027, 1));
    }

    /** O teto passado em até 20% fora do ano de abertura: sai do Simples em janeiro. */
    @Test
    void theCeilingWithinTheToleranceExcludesNextYear() {
        Map<YearMonth, BigDecimal> actual = monthly("400000", JANUARY, YearMonth.of(2026, 11));
        actual.put(YearMonth.of(2026, 12), d("500000")); // 4,9 mi: passou do teto, abaixo de 5,76 mi
        var ceiling = closed(RevenueLimits.Regime.SIMPLES, YearMonth.of(2024, 1), actual).limits().get(2);

        assertThat(ceiling.level()).isEqualTo(RevenueLimits.Level.EXCEEDED);
        assertThat(ceiling.effect()).isEqualTo(RevenueLimits.Effect.EXCLUDED_NEXT_YEAR);
        assertThat(ceiling.effectiveFrom()).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    void belowSeventyPercentThereIsNoAlert() {
        var result = closed(RevenueLimits.Regime.MEI, YearMonth.of(2024, 1), monthly("4000", JANUARY, YearMonth.of(2026, 12)));

        assertThat(result.limits().getFirst().level()).isEqualTo(RevenueLimits.Level.NONE);
        assertThat(result.alert()).isNull();
        assertThat(result.projectionTotal()).isNull();
    }
}
