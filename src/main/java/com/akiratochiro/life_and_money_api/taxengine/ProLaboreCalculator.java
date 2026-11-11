package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;

/**
 * INSS e IRRF de um pró-labore.
 *
 * <p><b>INSS:</b> 11% do bruto, limitado ao teto (Lei 8.212, art. 21 e art. 30, §4º). Não é a tabela
 * progressiva do empregado. Abaixo do salário mínimo, a contribuição não conta como tempo de
 * contribuição; o cálculo segue, e o resultado avisa.
 *
 * <p><b>IRRF:</b> base = bruto − maior entre (INSS) e o desconto simplificado (sem dependentes no MVP);
 * imposto pela tabela progressiva; depois a redução da Lei 15.270/2025 (art. 3º-A da Lei 9.250), que
 * usa o <em>rendimento bruto</em>, não a base, como nos exemplos oficiais da Receita:
 * <ul>
 *   <li>até o limite de isenção: a redução zera o imposto (até o valor máximo da lei);</li>
 *   <li>até o limite da redução parcial: A − B × bruto;</li>
 *   <li>acima: nenhuma.</li>
 * </ul>
 * A redução nunca deixa o imposto negativo. INSS, imposto e redução são arredondados para centavos.
 */
public final class ProLaboreCalculator {

    public record Result(
            BigDecimal gross,
            BigDecimal inssBase,
            BigDecimal inss,
            BigDecimal irrfBase,
            BigDecimal irrfBeforeReduction,
            BigDecimal reduction,
            BigDecimal irrf,
            BigDecimal net,
            boolean belowMinimumWage
    ) {
        public BigDecimal withheld() {
            return inss.add(irrf);
        }
    }

    private ProLaboreCalculator() {
    }

    public static Result of(BigDecimal gross, TaxTable table) {
        BigDecimal inssBase = Money.min(gross, table.get(TaxParameter.INSS_CEILING));
        BigDecimal inss = Money.cents(inssBase.multiply(table.get(TaxParameter.INSS_PRO_LABORE_RATE)));

        Irrf irrf = irrf(gross, inss, table);
        boolean belowMinimum = gross.compareTo(table.get(TaxParameter.MINIMUM_WAGE)) < 0;
        return new Result(gross, inssBase, inss, irrf.base(), irrf.beforeReduction(), irrf.reduction(), irrf.amount(),
                gross.subtract(inss).subtract(irrf.amount()), belowMinimum);
    }

    record Irrf(BigDecimal base, BigDecimal beforeReduction, BigDecimal reduction, BigDecimal amount) {
    }

    /** Separado das deduções para ser conferido contra os exemplos oficiais da Receita, que usam o INSS do empregado. */
    static Irrf irrf(BigDecimal gross, BigDecimal legalDeductions, TaxTable table) {
        BigDecimal deductions = Money.max(legalDeductions, table.get(TaxParameter.IRRF_SIMPLIFIED_DISCOUNT));
        BigDecimal base = Money.max(gross.subtract(deductions), BigDecimal.ZERO);
        BigDecimal tax = Money.cents(progressive(base, table));
        BigDecimal reduction = Money.min(Money.cents(reduction(gross, tax, table)), tax);
        return new Irrf(base, tax, reduction, tax.subtract(reduction));
    }

    static BigDecimal progressive(BigDecimal base, TaxTable table) {
        IrrfBracket bracket = table.irrfBrackets().stream()
                .filter(b -> b.incomeUpTo() == null || base.compareTo(b.incomeUpTo()) <= 0)
                .findFirst()
                .orElseThrow();
        return Money.max(base.multiply(bracket.rate()).subtract(bracket.deduction()), BigDecimal.ZERO);
    }

    static BigDecimal reduction(BigDecimal gross, BigDecimal tax, TaxTable table) {
        if (gross.compareTo(table.get(TaxParameter.IRRF_REDUCTION_FULL_LIMIT)) <= 0) {
            return Money.min(tax, table.get(TaxParameter.IRRF_REDUCTION_FULL_MAX));
        }
        if (gross.compareTo(table.get(TaxParameter.IRRF_REDUCTION_PARTIAL_LIMIT)) <= 0) {
            return Money.max(table.get(TaxParameter.IRRF_REDUCTION_A)
                    .subtract(table.get(TaxParameter.IRRF_REDUCTION_B).multiply(gross)), BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }
}
