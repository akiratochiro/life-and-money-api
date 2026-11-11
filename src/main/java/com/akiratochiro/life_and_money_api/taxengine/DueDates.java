package com.akiratochiro.life_and_money_api.taxengine;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Vencimentos. Os dois impostos mensais vencem no dia 20 do mês seguinte, mas resolvem o dia sem
 * expediente bancário em sentidos opostos:
 * <ul>
 *   <li><b>DAS e DAS-MEI:</b> vão para o próximo dia útil (Res. CGSN 140, art. 40, §3º;
 *       Res. CGSN 140, art. 40, § 3º, e art. 102, § 2º, para o DAS-MEI, conferido no texto oficial em 11/11/2026);</li>
 *   <li><b>DARF do pró-labore</b> (INSS pela DCTFWeb e IRRF): voltam para o dia útil anterior (Lei 8.212,
 *       art. 30, I, "b" e §2º; Lei 11.196, art. 70, I, "d").</li>
 * </ul>
 */
public final class DueDates {

    private DueDates() {
    }

    public static LocalDate das(YearMonth competence, BusinessDays days) {
        return postpone(competence.plusMonths(1).atDay(20), days);
    }

    public static LocalDate darf(YearMonth competence, BusinessDays days) {
        return anticipate(competence.plusMonths(1).atDay(20), days);
    }

    /** DEFIS do ano-calendário: 31 de março do ano seguinte (Res. CGSN 140, art. 72, § 1º, conferido no texto oficial em 11/11/2026). */
    public static LocalDate defis(int calendarYear) {
        return LocalDate.of(calendarYear + 1, 3, 31);
    }

    /** DASN-SIMEI do ano-calendário: último dia de maio do ano seguinte (Res. CGSN 140, art. 109, conferido no texto oficial em 11/11/2026). */
    public static LocalDate dasnSimei(int calendarYear) {
        return LocalDate.of(calendarYear + 1, 5, 31);
    }

    public static LocalDate postpone(LocalDate date, BusinessDays days) {
        LocalDate candidate = date;
        while (!days.isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public static LocalDate anticipate(LocalDate date, BusinessDays days) {
        LocalDate candidate = date;
        while (!days.isBusinessDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
