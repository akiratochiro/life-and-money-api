package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * O DAS do Simples Nacional nos Anexos III e V (LC 123, art. 18):
 * <ul>
 *   <li>a faixa sai do RBT12; a alíquota efetiva é (RBT12 × nominal − dedução) ÷ RBT12 (§1º-A);</li>
 *   <li>o DAS é a receita do mês × alíquota efetiva;</li>
 *   <li>na receita com ISS retido pelo tomador, a parte do ISS sai do DAS
 *       ({@link UnverifiedLegalSource#RES_CGSN_140_ART_25_PAR_9}). A parte do ISS é a efetiva × % do ISS
 *       na repartição, limitada a 5%: o excedente fica com os tributos federais (nota da faixa 5 do
 *       Anexo III).</li>
 * </ul>
 * Acima do sublimite (R$ 3,6 milhões) o ISS sai do DAS e as regras mudam; o app não calcula esse caso
 * (plano da Fase 4, item 1.5.2).
 */
public final class SimplesCalculator {

    public record Result(
            Annex annex,
            BigDecimal rbt12,
            int bracket,
            BigDecimal nominalRate,
            BigDecimal deduction,
            BigDecimal effectiveRate,
            BigDecimal issRate,
            BigDecimal revenue,
            BigDecimal revenueWithIssWithheld,
            BigDecimal amount
    ) {
    }

    private SimplesCalculator() {
    }

    /**
     * @param revenue                 toda a receita da competência
     * @param revenueWithIssWithheld  a parte dela cujo ISS o tomador reteve
     */
    public static Result das(Annex annex, BigDecimal rbt12, BigDecimal revenue, BigDecimal revenueWithIssWithheld,
                             TaxTable table) {
        SimplesBracket bracket = bracketFor(annex, rbt12, table);
        BigDecimal effective = effectiveRate(bracket, rbt12);
        BigDecimal issRate = Money.min(effective.multiply(bracket.issShare(), Money.RATE), table.get(TaxParameter.SIMPLES_ISS_CAP));
        BigDecimal withoutRetention = revenue.subtract(revenueWithIssWithheld);
        BigDecimal amount = withoutRetention.multiply(effective)
                .add(revenueWithIssWithheld.multiply(effective.subtract(issRate)));
        return new Result(annex, rbt12, bracket.bracket(), bracket.nominalRate(), bracket.deduction(), effective, issRate,
                revenue, revenueWithIssWithheld, Money.cents(amount));
    }

    /** Sem receita nos 12 meses, a fórmula dividiria por zero: vale a nominal da faixa 1. */
    static BigDecimal effectiveRate(SimplesBracket bracket, BigDecimal rbt12) {
        if (rbt12.signum() <= 0) {
            return bracket.nominalRate();
        }
        return rbt12.multiply(bracket.nominalRate()).subtract(bracket.deduction()).divide(rbt12, Money.RATE);
    }

    static SimplesBracket bracketFor(Annex annex, BigDecimal rbt12, TaxTable table) {
        if (rbt12.compareTo(table.get(TaxParameter.SIMPLES_CEILING)) > 0) {
            throw new TaxCalculationException("A receita dos últimos 12 meses (" + brl(rbt12) + ") passa do teto do "
                    + "Simples Nacional. Fale com o seu contador sobre a mudança de regime.");
        }
        if (rbt12.compareTo(table.get(TaxParameter.SIMPLES_SUBLIMIT)) > 0) {
            throw new TaxCalculationException("Acima de " + brl(table.get(TaxParameter.SIMPLES_SUBLIMIT)) + " nos últimos "
                    + "12 meses, o ISS sai do DAS e o cálculo muda. O app ainda não calcula esse caso: consulte o seu contador.");
        }
        return table.brackets(annex).stream()
                .filter(b -> rbt12.compareTo(b.revenueUpTo()) <= 0)
                .findFirst()
                .orElseThrow();
    }

    static String brl(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(value).replace(' ', ' ');
    }
}
