package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * A receita bruta dos 12 meses anteriores à competência, que escolhe a faixa (LC 123, art. 18,
 * §1º). Para quem tem menos de 12 meses, a receita é proporcionalizada (art. 18, §2º), pela regra
 * da Res. CGSN 140, art. 22 (conferido no texto oficial em 11/11/2026, §§ 2º a 4º):
 * <ul>
 *   <li>no 1º mês de atividade, a receita do próprio mês × 12;</li>
 *   <li>do 2º ao 12º mês, a média dos meses anteriores × 12;</li>
 *   <li>a partir do 13º, a soma dos 12 meses anteriores.</li>
 * </ul>
 */
public final class Rbt12 {

    public enum Method { FIRST_MONTH, PROPORTIONAL, TWELVE_MONTHS }

    /** A janela de meses usada: o RBT12 e o Fator R olham para os mesmos meses. */
    public record Window(YearMonth from, YearMonth to, Method method) {
        public long months() {
            return ChronoUnit.MONTHS.between(from, to) + 1;
        }
    }

    public record Result(BigDecimal value, Window window) {
    }

    private Rbt12() {
    }

    public static Window window(YearMonth competence, YearMonth activityStart) {
        if (competence.isBefore(activityStart)) {
            throw new IllegalArgumentException("Competence " + competence + " is before the activity start " + activityStart);
        }
        long monthsBefore = ChronoUnit.MONTHS.between(activityStart, competence);
        if (monthsBefore == 0) {
            return new Window(competence, competence, Method.FIRST_MONTH);
        }
        if (monthsBefore < 12) {
            return new Window(activityStart, competence.minusMonths(1), Method.PROPORTIONAL);
        }
        return new Window(competence.minusMonths(12), competence.minusMonths(1), Method.TWELVE_MONTHS);
    }

    public static Result of(YearMonth competence, YearMonth activityStart, Map<YearMonth, BigDecimal> revenueByMonth) {
        Window window = window(competence, activityStart);
        BigDecimal sum = sum(window, revenueByMonth);
        BigDecimal value = switch (window.method()) {
            case FIRST_MONTH -> sum.multiply(BigDecimal.valueOf(12));
            case PROPORTIONAL -> sum.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(window.months()), Money.RATE);
            case TWELVE_MONTHS -> sum;
        };
        return new Result(Money.cents(value), window);
    }

    static BigDecimal sum(Window window, Map<YearMonth, BigDecimal> byMonth) {
        BigDecimal sum = BigDecimal.ZERO;
        for (YearMonth month = window.from(); !month.isAfter(window.to()); month = month.plusMonths(1)) {
            sum = sum.add(byMonth.getOrDefault(month, BigDecimal.ZERO));
        }
        return sum;
    }
}
