package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * A tabela de 2026 escrita à mão, com os mesmos valores da V18 (conferidos na Fase 4). Os testes do
 * motor não dependem do banco; o teste de integração do {@code taxparam} confere que a V18 bate.
 */
public final class Tables2026 {

    private Tables2026() {
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static SimplesBracket b(Annex annex, int bracket, String upTo, String nominal, String deduction,
                                    String irpj, String csll, String cofins, String pis, String cpp, String iss) {
        return new SimplesBracket(annex, bracket, d(upTo), d(nominal), d(deduction), d(irpj), d(csll), d(cofins), d(pis),
                d(cpp), d(iss));
    }

    public static TaxTable table() {
        return new TaxTable(LocalDate.of(2026, 11, 1), Map.ofEntries(
                entry(TaxParameter.MINIMUM_WAGE, d("1621.00")),
                entry(TaxParameter.INSS_CEILING, d("8475.55")),
                entry(TaxParameter.INSS_PRO_LABORE_RATE, d("0.11")),
                entry(TaxParameter.IRRF_SIMPLIFIED_DISCOUNT, d("607.20")),
                entry(TaxParameter.IRRF_DEPENDENT_DEDUCTION, d("189.59")),
                entry(TaxParameter.IRRF_REDUCTION_FULL_LIMIT, d("5000.00")),
                entry(TaxParameter.IRRF_REDUCTION_FULL_MAX, d("312.89")),
                entry(TaxParameter.IRRF_REDUCTION_PARTIAL_LIMIT, d("7350.00")),
                entry(TaxParameter.IRRF_REDUCTION_A, d("978.62")),
                entry(TaxParameter.IRRF_REDUCTION_B, d("0.133145")),
                entry(TaxParameter.FATOR_R_THRESHOLD, d("0.28")),
                entry(TaxParameter.DAS_MEI_INSS_RATE, d("0.05")),
                entry(TaxParameter.DAS_MEI_ICMS, d("1.00")),
                entry(TaxParameter.DAS_MEI_ISS, d("5.00")),
                entry(TaxParameter.MEI_ANNUAL_LIMIT, d("81000.00")),
                entry(TaxParameter.MEI_MONTHLY_LIMIT, d("6750.00")),
                entry(TaxParameter.MEI_TOLERANCE, d("0.20")),
                entry(TaxParameter.SIMPLES_EPP_THRESHOLD, d("360000.00")),
                entry(TaxParameter.SIMPLES_SUBLIMIT, d("3600000.00")),
                entry(TaxParameter.SIMPLES_CEILING, d("4800000.00")),
                entry(TaxParameter.SIMPLES_TOLERANCE, d("0.20")),
                entry(TaxParameter.SIMPLES_ISS_CAP, d("0.05")),
                entry(TaxParameter.DIVIDENDS_MONTHLY_EXEMPTION, d("50000.00")),
                entry(TaxParameter.DIVIDENDS_IRRF_RATE, d("0.10"))),
                List.of(
                        b(Annex.III, 1, "180000.00", "0.06", "0", "0.04", "0.035", "0.1282", "0.0278", "0.434", "0.335"),
                        b(Annex.III, 2, "360000.00", "0.112", "9360.00", "0.04", "0.035", "0.1405", "0.0305", "0.434", "0.32"),
                        b(Annex.III, 3, "720000.00", "0.135", "17640.00", "0.04", "0.035", "0.1364", "0.0296", "0.434", "0.325"),
                        b(Annex.III, 4, "1800000.00", "0.16", "35640.00", "0.04", "0.035", "0.1364", "0.0296", "0.434", "0.325"),
                        b(Annex.III, 5, "3600000.00", "0.21", "125640.00", "0.04", "0.035", "0.1282", "0.0278", "0.434", "0.335"),
                        b(Annex.III, 6, "4800000.00", "0.33", "648000.00", "0.35", "0.15", "0.1603", "0.0347", "0.305", "0"),
                        b(Annex.V, 1, "180000.00", "0.155", "0", "0.25", "0.15", "0.141", "0.0305", "0.2885", "0.14"),
                        b(Annex.V, 2, "360000.00", "0.18", "4500.00", "0.23", "0.15", "0.141", "0.0305", "0.2785", "0.17"),
                        b(Annex.V, 3, "720000.00", "0.195", "9900.00", "0.24", "0.15", "0.1492", "0.0323", "0.2385", "0.19"),
                        b(Annex.V, 4, "1800000.00", "0.205", "17100.00", "0.21", "0.15", "0.1574", "0.0341", "0.2385", "0.21"),
                        b(Annex.V, 5, "3600000.00", "0.23", "62100.00", "0.23", "0.125", "0.141", "0.0305", "0.2385", "0.235"),
                        b(Annex.V, 6, "4800000.00", "0.305", "540000.00", "0.35", "0.155", "0.1644", "0.0356", "0.295", "0")),
                List.of(
                        new IrrfBracket(1, d("2428.80"), d("0"), d("0")),
                        new IrrfBracket(2, d("2826.65"), d("0.075"), d("182.16")),
                        new IrrfBracket(3, d("3751.05"), d("0.15"), d("394.16")),
                        new IrrfBracket(4, d("4664.68"), d("0.225"), d("675.49")),
                        new IrrfBracket(5, null, d("0.275"), d("908.73"))));
    }
}
