package com.akiratochiro.life_and_money_api.bill;

import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expande as contas nas ocorrências de um intervalo de datas. As ocorrências não existem no
 * banco: são calculadas aqui, a cada leitura, a partir da conta e dos termos vigentes em cada mês.
 */
final class BillCalendar {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    record Occurrence(ScheduledBill bill, BillTerms terms, YearMonth month, LocalDate dueDate) {
    }

    private BillCalendar() {
    }

    /**
     * As ocorrências que vencem entre {@code from} e {@code to}, inclusive, da mais cedo para a
     * mais tarde. Um intervalo pode atravessar meses e anos (28/12 a 03/01).
     */
    static List<Occurrence> between(List<ScheduledBill> bills, Map<Long, List<BillTerms>> termsByBill,
                                    LocalDate from, LocalDate to) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            for (ScheduledBill bill : bills) {
                if (!bill.occursIn(month)) {
                    continue;
                }
                BillTerms terms = termsIn(termsByBill.getOrDefault(bill.getId(), List.of()), month);
                if (terms == null) {
                    continue;
                }
                LocalDate dueDate = terms.dueDateIn(month);
                if (!dueDate.isBefore(from) && !dueDate.isAfter(to)) {
                    occurrences.add(new Occurrence(bill, terms, month, dueDate));
                }
            }
        }
        occurrences.sort(Comparator.comparing(Occurrence::dueDate)
                .thenComparing(occurrence -> occurrence.terms().getName(), byName()));
        return occurrences;
    }

    /**
     * Ordem alfabética do português: "Água" antes de "internet". O {@code CASE_INSENSITIVE_ORDER}
     * compara pelo código do caractere, e "Á" vem depois de todas as letras sem acento. Uma
     * instância por chamada, porque o {@link Collator} não promete ser seguro entre threads.
     */
    static Comparator<String> byName() {
        Collator collator = Collator.getInstance(PT_BR);
        collator.setStrength(Collator.SECONDARY);
        return collator::compare;
    }

    /** Os termos que valiam no mês: é o que faz um mês passado mostrar o valor da época. */
    static BillTerms termsIn(List<BillTerms> terms, YearMonth month) {
        return terms.stream().filter(candidate -> candidate.isValidIn(month)).findFirst().orElse(null);
    }
}
