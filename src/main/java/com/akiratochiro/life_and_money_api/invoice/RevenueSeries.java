package com.akiratochiro.life_and_money_api.invoice;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Agrupa a receita das notas por dia, semana ou mês. Função pura (decisão 22): recebe as receitas
 * por data de competência e devolve um ponto por período, com zero nos períodos sem nota, para o
 * gráfico não pular meses.
 *
 * <p>O intervalo é alargado até os limites do período: na semana, da segunda ao domingo; no mês, do
 * dia 1 ao último dia. Assim cada barra cobre um período inteiro, e uma semana que começa em outubro
 * e acaba em novembro aparece inteira, identificada pela segunda-feira.
 */
public final class RevenueSeries {

    static final int MAX_DAYS = 93;
    static final int MAX_WEEKS = 53;
    static final int MAX_MONTHS = 36;

    /** A receita de uma nota ativa: serviço menos desconto incondicional, na data de competência. */
    public record Entry(LocalDate competenceDate, BigDecimal amount) {
    }

    public record Point(LocalDate start, LocalDate end, BigDecimal amount, int invoices) {
    }

    /** O intervalo já alargado até os limites dos períodos. */
    public record Range(LocalDate from, LocalDate to) {
    }

    private RevenueSeries() {
    }

    /** @throws InvalidRevenueRangeException com a mensagem para o usuário, se o intervalo não vale */
    public static Range range(LocalDate from, LocalDate to, RevenueGranularity granularity) {
        if (from.isAfter(to)) {
            throw new InvalidRevenueRangeException("A data inicial precisa ser antes da final.");
        }
        Range range = switch (granularity) {
            case DAY -> new Range(from, to);
            case WEEK -> new Range(from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case MONTH -> new Range(from.withDayOfMonth(1), to.with(TemporalAdjusters.lastDayOfMonth()));
        };
        long periods = switch (granularity) {
            case DAY -> ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
            case WEEK -> ChronoUnit.WEEKS.between(range.from(), range.to().plusDays(1));
            case MONTH -> ChronoUnit.MONTHS.between(range.from(), range.to().plusDays(1));
        };
        long max = switch (granularity) {
            case DAY -> MAX_DAYS;
            case WEEK -> MAX_WEEKS;
            case MONTH -> MAX_MONTHS;
        };
        if (periods > max) {
            String unit = switch (granularity) {
                case DAY -> "dias";
                case WEEK -> "semanas";
                case MONTH -> "meses";
            };
            throw new InvalidRevenueRangeException("Escolha um período de no máximo " + max + " " + unit + ".");
        }
        return range;
    }

    /** Um ponto por período do intervalo, em ordem, com as entradas fora do intervalo ignoradas. */
    public static List<Point> build(Range range, RevenueGranularity granularity, List<Entry> entries) {
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        TreeMap<LocalDate, Integer> counts = new TreeMap<>();
        for (LocalDate start = range.from(); !start.isAfter(range.to()); start = next(start, granularity)) {
            amounts.put(start, BigDecimal.ZERO);
            counts.put(start, 0);
        }
        for (Entry entry : entries) {
            if (entry.competenceDate().isBefore(range.from()) || entry.competenceDate().isAfter(range.to())) {
                continue;
            }
            LocalDate start = startOf(entry.competenceDate(), granularity);
            amounts.merge(start, entry.amount(), BigDecimal::add);
            counts.merge(start, 1, Integer::sum);
        }
        List<Point> points = new ArrayList<>();
        amounts.forEach((start, amount) ->
                points.add(new Point(start, next(start, granularity).minusDays(1), amount, counts.get(start))));
        return points;
    }

    static LocalDate startOf(LocalDate date, RevenueGranularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate start, RevenueGranularity granularity) {
        return switch (granularity) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
    }
}
