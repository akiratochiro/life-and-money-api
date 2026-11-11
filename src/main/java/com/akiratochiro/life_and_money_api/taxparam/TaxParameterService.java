package com.akiratochiro.life_and_money_api.taxparam;

import com.akiratochiro.life_and_money_api.taxengine.TaxParameter;
import com.akiratochiro.life_and_money_api.taxengine.TaxTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Monta a {@link TaxTable} de uma data a partir das tabelas com vigência: para cada parâmetro, o
 * valor mais recente que já valia; para as faixas, o conjunto mais recente que já valia. É a ponte
 * entre o banco e o motor, que não conhece o banco.
 */
@Service
public class TaxParameterService {

    /** A primeira competência com todos os parâmetros: o app calcula a partir daqui (plano da Fase 4, item 1.5.1). */
    public static final YearMonth FIRST_SUPPORTED = YearMonth.of(2026, 1);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final TaxParameterRepository parameters;
    private final SimplesBracketRepository simples;
    private final IrrfBracketRepository irrf;

    public TaxParameterService(TaxParameterRepository parameters, SimplesBracketRepository simples, IrrfBracketRepository irrf) {
        this.parameters = parameters;
        this.simples = simples;
        this.irrf = irrf;
    }

    /** Os parâmetros que valiam na competência (no dia 1 do mês). */
    @Transactional(readOnly = true)
    public TaxTable tableFor(YearMonth competence) {
        if (competence.isBefore(FIRST_SUPPORTED)) {
            throw new TaxParametersUnavailableException("O app calcula impostos a partir de " + MONTH.format(FIRST_SUPPORTED)
                    + ". Para " + MONTH.format(competence) + ", registre o valor que foi pago.");
        }
        LocalDate date = competence.atDay(1);
        Map<TaxParameter, BigDecimal> values = new EnumMap<>(TaxParameter.class);
        latestParameters(date).forEach((key, row) -> values.put(key, row.getValue()));
        if (values.size() < TaxParameter.values().length) {
            throw new TaxParametersUnavailableException("Faltam parâmetros tributários para " + MONTH.format(competence) + ".");
        }
        return new TaxTable(date, values,
                latestSet(simples.findAllByValidFromLessThanEqual(date), SimplesBracketRow::getValidFrom).stream()
                        .map(SimplesBracketRow::toBracket).toList(),
                latestSet(irrf.findAllByValidFromLessThanEqual(date), IrrfBracketRow::getValidFrom).stream()
                        .map(IrrfBracketRow::toBracket).toList());
    }

    /** Tudo o que vale numa data, com a fonte de cada valor: para conferência na tela. */
    @Transactional(readOnly = true)
    public TaxParametersResponse describe(LocalDate date) {
        return new TaxParametersResponse(date,
                latestParameters(date).values().stream()
                        .map(row -> new TaxParametersResponse.Parameter(row.getKey(), row.getValue(), row.getValidFrom(), row.getSource()))
                        .toList(),
                latestSet(simples.findAllByValidFromLessThanEqual(date), SimplesBracketRow::getValidFrom).stream()
                        .map(row -> new TaxParametersResponse.Simples(row.toBracket(), row.getValidFrom(), row.getSource()))
                        .toList(),
                latestSet(irrf.findAllByValidFromLessThanEqual(date), IrrfBracketRow::getValidFrom).stream()
                        .map(row -> new TaxParametersResponse.Irrf(row.toBracket(), row.getValidFrom(), row.getSource()))
                        .toList());
    }

    /** Linhas em ordem de vigência: a última de cada parâmetro sobrescreve as anteriores. */
    private Map<TaxParameter, TaxParameterRow> latestParameters(LocalDate date) {
        Map<TaxParameter, TaxParameterRow> latest = new EnumMap<>(TaxParameter.class);
        for (TaxParameterRow row : parameters.findAllByValidFromLessThanEqualOrderByValidFromAsc(date)) {
            latest.put(row.getKey(), row);
        }
        return latest;
    }

    /** As faixas mudam juntas: vale o conjunto inteiro com a vigência mais recente. */
    private static <T> List<T> latestSet(List<T> rows, Function<T, LocalDate> validFrom) {
        Optional<LocalDate> latest = rows.stream().map(validFrom).max(LocalDate::compareTo);
        return latest.map(date -> rows.stream().filter(row -> validFrom.apply(row).equals(date)).toList()).orElse(List.of());
    }
}
