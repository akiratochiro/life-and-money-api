package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Os limites de faturamento do ano-calendário (Fase 10), conferidos no Planalto em 12/11/2026:
 * <ul>
 *   <li><b>MEI</b>: o limite anual; no ano de abertura, o mensal × meses de atividade, a fração contando
 *       como mês inteiro (LC 123, art. 18-A, §§1º e 2º). Até 20% acima, sai do MEI em janeiro; acima,
 *       o desenquadramento retroage a janeiro ou à abertura (§7º, III e IV).</li>
 *   <li><b>ME → EPP</b> (R$ 360 mil): passa a EPP no ano seguinte (art. 3º, I e §7º).</li>
 *   <li><b>Sublimite</b> (R$ 3,6 mi): o ISS e o ICMS saem do DAS no mês seguinte ao excesso, ou no ano
 *       seguinte se o excesso for de até 20% (art. 19, §4º; art. 20, §§1º e 1º-A).</li>
 *   <li><b>Teto</b> (R$ 4,8 mi): sai do Simples no mês seguinte, ou no ano seguinte se o excesso for de até
 *       20% (art. 3º, II, §§9º e 9º-A). No ano de abertura, os limites do Simples são proporcionais (§2º), e o
 *       excesso de mais de 20% retroage ao início das atividades (§§10 a 12).</li>
 * </ul>
 * Puro: recebe a receita por mês e a {@link TaxTable} do ano. As frases ficam fora, no {@code tax}.
 */
public final class RevenueLimits {

    public enum Regime { MEI, SIMPLES }

    public enum Kind { MEI_ANNUAL, SIMPLES_EPP, SIMPLES_SUBLIMIT, SIMPLES_CEILING }

    public enum Level { NONE, WARNING, NEAR, EXCEEDED, EXCEEDED_TOLERANCE }

    /** O que acontece por ter passado, e a partir de quando. */
    public enum Effect {
        MEI_LEAVES_NEXT_YEAR, MEI_RETROACTIVE,
        BECOMES_EPP_NEXT_YEAR,
        ISS_OUT_NEXT_YEAR, ISS_OUT_NEXT_MONTH, ISS_OUT_RETROACTIVE,
        EXCLUDED_NEXT_YEAR, EXCLUDED_NEXT_MONTH, EXCLUDED_RETROACTIVE
    }

    static final BigDecimal WARNING = new BigDecimal("0.70");
    static final BigDecimal NEAR = new BigDecimal("0.90");

    public record Limit(Kind kind, BigDecimal amount, BigDecimal toleranceAmount, BigDecimal ratio, Level level,
                        YearMonth exceededIn, YearMonth toleranceExceededIn, YearMonth projectedIn,
                        Effect effect, YearMonth effectiveFrom) {
        public boolean exceeded() {
            return exceededIn != null;
        }
    }

    /**
     * @param projectionTotal ausente quando não há projeção (ano fechado)
     * @param alert           o limite mais alto que pede atenção; ausente se nenhum
     */
    public record Result(int year, boolean openingYear, int activityMonths, BigDecimal revenue, BigDecimal projectionTotal,
                         List<Limit> limits, Kind alert) {
    }

    private RevenueLimits() {
    }

    /**
     * @param actual        a receita de cada mês do ano (notas lançadas)
     * @param projectedFrom o primeiro mês projetado (o corrente); ausente num ano fechado
     * @param projected     a receita projetada de {@code projectedFrom} a dezembro, no lugar da real
     */
    public static Result of(int year, Regime regime, YearMonth activityStart, Map<YearMonth, BigDecimal> actual,
                            YearMonth projectedFrom, Map<YearMonth, BigDecimal> projected, TaxTable table) {
        boolean openingYear = activityStart.getYear() == year;
        int months = openingYear ? 13 - activityStart.getMonthValue() : 12;
        BigDecimal revenue = sum(actual, year, null, Map.of());
        BigDecimal projectionTotal = projectedFrom == null ? null : sum(actual, year, projectedFrom, projected);

        List<Limit> limits = new ArrayList<>();
        if (regime == Regime.MEI) {
            BigDecimal amount = openingYear
                    ? Money.cents(table.get(TaxParameter.MEI_MONTHLY_LIMIT).multiply(BigDecimal.valueOf(months)))
                    : table.get(TaxParameter.MEI_ANNUAL_LIMIT);
            limits.add(limit(Kind.MEI_ANNUAL, amount, tolerance(amount, table.get(TaxParameter.MEI_TOLERANCE)), year,
                    activityStart, openingYear, actual, projectedFrom, projected));
        } else {
            BigDecimal share = BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), Money.RATE);
            BigDecimal tolerance = table.get(TaxParameter.SIMPLES_TOLERANCE);
            BigDecimal epp = Money.cents(table.get(TaxParameter.SIMPLES_EPP_THRESHOLD).multiply(share));
            BigDecimal sublimit = Money.cents(table.get(TaxParameter.SIMPLES_SUBLIMIT).multiply(share));
            BigDecimal ceiling = Money.cents(table.get(TaxParameter.SIMPLES_CEILING).multiply(share));
            limits.add(limit(Kind.SIMPLES_EPP, epp, null, year, activityStart, openingYear, actual, projectedFrom, projected));
            limits.add(limit(Kind.SIMPLES_SUBLIMIT, sublimit, tolerance(sublimit, tolerance), year, activityStart, openingYear,
                    actual, projectedFrom, projected));
            limits.add(limit(Kind.SIMPLES_CEILING, ceiling, tolerance(ceiling, tolerance), year, activityStart, openingYear,
                    actual, projectedFrom, projected));
        }

        // O mais alto que pede atenção: quem passou dos R$ 3,6 mi não precisa mais do aviso dos R$ 360 mil.
        Kind alert = null;
        for (Limit limit : limits) {
            if (limit.level() != Level.NONE) {
                alert = limit.kind();
            }
        }
        return new Result(year, openingYear, months, revenue, projectionTotal, limits, alert);
    }

    private static BigDecimal tolerance(BigDecimal amount, BigDecimal rate) {
        return Money.cents(amount.multiply(BigDecimal.ONE.add(rate)));
    }

    private static Limit limit(Kind kind, BigDecimal amount, BigDecimal toleranceAmount, int year, YearMonth start,
                               boolean openingYear, Map<YearMonth, BigDecimal> actual, YearMonth projectedFrom,
                               Map<YearMonth, BigDecimal> projected) {
        BigDecimal revenue = sum(actual, year, null, Map.of());
        BigDecimal ratio = revenue.divide(amount, 4, RoundingMode.HALF_UP);
        YearMonth exceededIn = firstAbove(actual, year, null, Map.of(), amount);
        YearMonth toleranceExceededIn = toleranceAmount == null ? null : firstAbove(actual, year, null, Map.of(), toleranceAmount);
        YearMonth projectedIn = exceededIn != null || projectedFrom == null ? null
                : firstAbove(actual, year, projectedFrom, projected, amount);

        Level level;
        if (toleranceExceededIn != null) {
            level = Level.EXCEEDED_TOLERANCE;
        } else if (exceededIn != null) {
            level = Level.EXCEEDED;
        } else if (ratio.compareTo(NEAR) >= 0) {
            level = Level.NEAR;
        } else if (ratio.compareTo(WARNING) >= 0) {
            level = Level.WARNING;
        } else {
            level = Level.NONE;
        }

        Effect effect = null;
        YearMonth effectiveFrom = null;
        YearMonth nextYear = YearMonth.of(year + 1, 1);
        if (exceededIn != null) {
            boolean beyond = toleranceExceededIn != null;
            switch (kind) {
                case MEI_ANNUAL -> {
                    effect = beyond ? Effect.MEI_RETROACTIVE : Effect.MEI_LEAVES_NEXT_YEAR;
                    effectiveFrom = beyond ? (openingYear ? start : YearMonth.of(year, 1)) : nextYear;
                }
                case SIMPLES_EPP -> {
                    effect = Effect.BECOMES_EPP_NEXT_YEAR;
                    effectiveFrom = nextYear;
                }
                case SIMPLES_SUBLIMIT -> {
                    effect = !beyond ? Effect.ISS_OUT_NEXT_YEAR : openingYear ? Effect.ISS_OUT_RETROACTIVE : Effect.ISS_OUT_NEXT_MONTH;
                    effectiveFrom = !beyond ? nextYear : openingYear ? start : toleranceExceededIn.plusMonths(1);
                }
                case SIMPLES_CEILING -> {
                    effect = !beyond ? Effect.EXCLUDED_NEXT_YEAR : openingYear ? Effect.EXCLUDED_RETROACTIVE : Effect.EXCLUDED_NEXT_MONTH;
                    effectiveFrom = !beyond ? nextYear : openingYear ? start : toleranceExceededIn.plusMonths(1);
                }
            }
        }
        return new Limit(kind, amount, toleranceAmount, ratio, level, exceededIn, toleranceExceededIn, projectedIn, effect, effectiveFrom);
    }

    /** O mês em que a receita acumulada do ano passa (estritamente) do valor; ausente se não passa. */
    private static YearMonth firstAbove(Map<YearMonth, BigDecimal> actual, int year, YearMonth projectedFrom,
                                        Map<YearMonth, BigDecimal> projected, BigDecimal amount) {
        BigDecimal total = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            total = total.add(valueIn(YearMonth.of(year, m), actual, projectedFrom, projected));
            if (total.compareTo(amount) > 0) {
                return YearMonth.of(year, m);
            }
        }
        return null;
    }

    private static BigDecimal sum(Map<YearMonth, BigDecimal> actual, int year, YearMonth projectedFrom, Map<YearMonth, BigDecimal> projected) {
        BigDecimal total = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            total = total.add(valueIn(YearMonth.of(year, m), actual, projectedFrom, projected));
        }
        return total;
    }

    private static BigDecimal valueIn(YearMonth month, Map<YearMonth, BigDecimal> actual, YearMonth projectedFrom,
                                      Map<YearMonth, BigDecimal> projected) {
        if (projectedFrom != null && !month.isBefore(projectedFrom)) {
            return projected.getOrDefault(month, BigDecimal.ZERO);
        }
        return actual.getOrDefault(month, BigDecimal.ZERO);
    }
}
