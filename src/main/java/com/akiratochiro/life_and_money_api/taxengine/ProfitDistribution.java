package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.util.List;

/**
 * As contas da distribuição de lucros (Fase 6), puras como o resto do motor. Nenhuma delas bloqueia
 * nada: alimentam avisos, porque a isenção depende da contabilidade (que o app não vê) e a retenção
 * no Simples é contestada.
 */
public final class ProfitDistribution {

    private ProfitDistribution() {
    }

    /**
     * A retenção sugerida da Lei 15.270/2025 (Lei 9.250, art. 6º-A): acima do limite mensal pago por uma
     * mesma empresa a uma mesma pessoa, a alíquota incide sobre o <b>total</b> do mês, não só sobre o
     * excedente. No Simples, a Receita entende que vale também; a aplicação é contestada
     * ({@link UnverifiedLegalSource#LEI_15270_SIMPLES}). Zero até o limite.
     */
    public static BigDecimal suggestedWithholding(BigDecimal monthTotal, TaxTable table) {
        if (monthTotal.compareTo(table.get(TaxParameter.DIVIDENDS_MONTHLY_EXEMPTION)) <= 0) {
            return BigDecimal.ZERO;
        }
        return Money.cents(monthTotal.multiply(table.get(TaxParameter.DIVIDENDS_IRRF_RATE)));
    }

    /**
     * O IRPJ que está dentro do DAS de um mês: a receita × a alíquota efetiva × a fração do IRPJ na
     * faixa. Ignora a redistribuição quando o ISS bate no teto de 5% (diferença pequena, a favor da
     * estimativa conservadora do limite).
     */
    public static BigDecimal irpjInDas(BigDecimal revenue, BigDecimal effectiveRate, BigDecimal irpjShare) {
        return Money.cents(revenue.multiply(effectiveRate).multiply(irpjShare));
    }

    /** Um mês na conta do limite: a receita, o percentual de presunção e o IRPJ já pago no DAS (zero no MEI). */
    public record Month(BigDecimal revenue, BigDecimal presumption, BigDecimal irpjInDas) {
    }

    /**
     * O lucro isento estimado sem contabilidade (LC 123, art. 14; Res. CGSN 140, art. 145 —
     * {@link UnverifiedLegalSource#RES_CGSN_140_ART_145}): a presunção da Lei 9.249, art. 15, sobre a
     * receita, menos o IRPJ devido no Simples no período. Nunca negativo.
     */
    public static BigDecimal exemptionLimit(List<Month> months) {
        BigDecimal limit = BigDecimal.ZERO;
        for (Month month : months) {
            limit = limit.add(month.revenue().multiply(month.presumption())).subtract(month.irpjInDas());
        }
        return Money.cents(limit.max(BigDecimal.ZERO));
    }
}
