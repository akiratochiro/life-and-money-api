package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;

/**
 * A calculadora de pró-labore: com um faturamento mensal constante, qual pró-labore leva o Fator R
 * à meta, e quanto custa cada caminho.
 * <ul>
 *   <li><b>Anexo III:</b> o pró-labore mínimo para a folha atingir a meta (meta × faturamento − outras
 *       folhas), nunca abaixo do salário mínimo;</li>
 *   <li><b>Anexo V:</b> o pró-labore no salário mínimo.</li>
 * </ul>
 * O custo de cada caminho é DAS + INSS + IRRF do mês. Com faturamento constante, o RBT12 é 12 × o
 * mês, e a folha precisa ser meta × faturamento por mês. A meta pode ser a da empresa (por exemplo,
 * 30% para ter folga), mas nunca abaixo do limite legal, que é o que decide o Anexo.
 */
public final class ProLaboreAdvisor {

    public record Scenario(Annex annex, BigDecimal proLabore, SimplesCalculator.Result das, ProLaboreCalculator.Result proLaboreTaxes,
                           BigDecimal totalCost) {
    }

    public record Result(BigDecimal monthlyRevenue, BigDecimal target, Scenario annexIII, Scenario annexV, BigDecimal savings) {
        public Annex cheaper() {
            return savings.signum() > 0 ? Annex.III : Annex.V;
        }
    }

    private ProLaboreAdvisor() {
    }

    public static Result advise(BigDecimal monthlyRevenue, BigDecimal otherMonthlyPayroll, BigDecimal companyTarget, TaxTable table) {
        BigDecimal target = Money.max(companyTarget, table.get(TaxParameter.FATOR_R_THRESHOLD));
        BigDecimal minimumWage = table.get(TaxParameter.MINIMUM_WAGE);
        BigDecimal rbt12 = monthlyRevenue.multiply(BigDecimal.valueOf(12));

        BigDecimal needed = Money.centsUp(target.multiply(monthlyRevenue).subtract(otherMonthlyPayroll));
        Scenario iii = scenario(Annex.III, Money.max(needed, minimumWage), monthlyRevenue, rbt12, table);
        Scenario v = scenario(Annex.V, minimumWage, monthlyRevenue, rbt12, table);
        return new Result(monthlyRevenue, target, iii, v, v.totalCost().subtract(iii.totalCost()));
    }

    private static Scenario scenario(Annex annex, BigDecimal proLabore, BigDecimal revenue, BigDecimal rbt12, TaxTable table) {
        SimplesCalculator.Result das = SimplesCalculator.das(annex, rbt12, revenue, BigDecimal.ZERO, table);
        ProLaboreCalculator.Result taxes = ProLaboreCalculator.of(proLabore, table);
        return new Scenario(annex, proLabore, das, taxes, das.amount().add(taxes.withheld()));
    }
}
