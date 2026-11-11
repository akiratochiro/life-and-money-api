package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;

/**
 * O DAS-MEI: valor fixo por mês, que não depende da receita (LC 123, art. 18-A, §3º, V; Res. CGSN
 * 140, art. 101). INSS de 5% do salário mínimo, mais R$ 1 de ICMS (comércio ou indústria) e/ou R$ 5
 * de ISS (serviços).
 */
public final class SimeiCalculator {

    /** Espelha {@code company.MeiActivity}, para o motor não depender do pacote da empresa. */
    public enum Activity { SERVICES, TRADE_OR_INDUSTRY, BOTH }

    public record Result(BigDecimal inss, BigDecimal icms, BigDecimal iss, BigDecimal amount) {
    }

    private SimeiCalculator() {
    }

    public static Result das(Activity activity, TaxTable table) {
        BigDecimal inss = Money.cents(table.get(TaxParameter.MINIMUM_WAGE).multiply(table.get(TaxParameter.DAS_MEI_INSS_RATE)));
        BigDecimal icms = activity == Activity.SERVICES ? BigDecimal.ZERO : table.get(TaxParameter.DAS_MEI_ICMS);
        BigDecimal iss = activity == Activity.TRADE_OR_INDUSTRY ? BigDecimal.ZERO : table.get(TaxParameter.DAS_MEI_ISS);
        return new Result(inss, Money.cents(icms), Money.cents(iss), Money.cents(inss.add(icms).add(iss)));
    }
}
