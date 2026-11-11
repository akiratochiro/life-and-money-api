package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Os parâmetros que valiam numa data: é tudo o que o motor precisa saber do mundo. Montada pelo
 * {@code taxparam} a partir do banco, ou à mão nos testes.
 */
public record TaxTable(LocalDate validOn, Map<TaxParameter, BigDecimal> parameters,
                       List<SimplesBracket> simplesBrackets, List<IrrfBracket> irrfBrackets) {

    public TaxTable {
        parameters = Map.copyOf(parameters);
        simplesBrackets = simplesBrackets.stream()
                .sorted(Comparator.comparing(SimplesBracket::annex).thenComparingInt(SimplesBracket::bracket)).toList();
        irrfBrackets = irrfBrackets.stream().sorted(Comparator.comparingInt(IrrfBracket::bracket)).toList();
    }

    public BigDecimal get(TaxParameter parameter) {
        BigDecimal value = parameters.get(parameter);
        if (value == null) {
            throw new IllegalStateException("Tax parameter " + parameter + " is missing for " + validOn);
        }
        return value;
    }

    public List<SimplesBracket> brackets(Annex annex) {
        return simplesBrackets.stream().filter(b -> b.annex() == annex).toList();
    }
}
