package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * O monitor do Fator R (Fase 8): o Fator R da próxima competência com o mês corrente projetado, e o
 * que fazer quando ele fica abaixo da meta.
 *
 * <p>O Fator R de uma competência usa os meses anteriores a ela (a janela do RBT12), então o mês
 * corrente decide o Anexo do mês que vem. Quem chama já põe na folha e na receita do mês corrente a
 * projeção: max(notas, faturamento esperado) e o pró-labore registrado ou planejado.
 *
 * <p>A meta é a da empresa, nunca abaixo do limite legal; só o limite decide o Anexo (Fase 0, 1.1).
 */
public final class FatorRProjection {

    public enum Status {
        /** Na meta ou acima. */
        OK,
        /** Entre o limite legal e a meta: ainda Anexo III, sem a folga escolhida. */
        BELOW_TARGET,
        /** Abaixo do limite legal: a competência vai para o Anexo V. */
        BELOW_THRESHOLD,
        /** Sem receita na janela: o Fator R é o valor fixo da Res. CGSN 140 e não há o que corrigir. */
        NO_REVENUE
    }

    /**
     * Quanto aumentar o pró-labore do mês corrente para chegar à meta, e quanto isso economiza na
     * próxima competência. A economia só existe abaixo do limite legal.
     */
    public record Suggestion(BigDecimal missingPayroll, BigDecimal currentProLabore, BigDecimal suggestedProLabore,
                             BigDecimal dasAnnexIII, BigDecimal dasAnnexV, BigDecimal extraProLaboreTaxes, BigDecimal savings) {
    }

    public record Result(FatorR.Result fatorR, BigDecimal target, BigDecimal threshold, Status status, Suggestion suggestion) {
    }

    private FatorRProjection() {
    }

    /**
     * @param competence        a competência cujo Anexo está em jogo (a próxima)
     * @param payrollByMonth    a folha por mês, com o mês corrente projetado
     * @param revenueByMonth    a receita por mês, com o mês corrente projetado
     * @param currentProLabore  o pró-labore do sócio "sou eu" no mês corrente (registrado ou planejado; zero se não há)
     * @param estimatedRevenue  a receita que se espera na competência, para estimar os dois DAS
     */
    public static Result project(YearMonth competence, YearMonth activityStart, Map<YearMonth, BigDecimal> payrollByMonth,
                                 Map<YearMonth, BigDecimal> revenueByMonth, BigDecimal companyTarget, BigDecimal currentProLabore,
                                 BigDecimal estimatedRevenue, TaxTable table) {
        FatorR.Result fatorR = FatorR.of(competence, activityStart, payrollByMonth, revenueByMonth, table);
        BigDecimal threshold = table.get(TaxParameter.FATOR_R_THRESHOLD);
        BigDecimal target = Money.max(companyTarget, threshold);

        if (fatorR.revenue().signum() == 0) {
            return new Result(fatorR, target, threshold, Status.NO_REVENUE, null);
        }
        if (fatorR.ratio().compareTo(target) >= 0) {
            return new Result(fatorR, target, threshold, Status.OK, null);
        }
        Status status = fatorR.ratio().compareTo(threshold) >= 0 ? Status.BELOW_TARGET : Status.BELOW_THRESHOLD;

        BigDecimal missing = Money.centsUp(target.multiply(fatorR.revenue()).subtract(fatorR.payroll()));
        BigDecimal suggested = Money.max(currentProLabore.add(missing), table.get(TaxParameter.MINIMUM_WAGE));
        if (status == Status.BELOW_TARGET) {
            return new Result(fatorR, target, threshold, status,
                    new Suggestion(missing, currentProLabore, suggested, null, null, null, null));
        }

        BigDecimal rbt12 = Rbt12.of(competence, activityStart, revenueByMonth).value();
        BigDecimal dasIII = SimplesCalculator.das(Annex.III, rbt12, estimatedRevenue, BigDecimal.ZERO, table).amount();
        BigDecimal dasV = SimplesCalculator.das(Annex.V, rbt12, estimatedRevenue, BigDecimal.ZERO, table).amount();
        BigDecimal before = currentProLabore.signum() > 0 ? ProLaboreCalculator.of(currentProLabore, table).withheld() : BigDecimal.ZERO;
        BigDecimal extraTaxes = ProLaboreCalculator.of(suggested, table).withheld().subtract(before);
        BigDecimal savings = dasV.subtract(dasIII).subtract(extraTaxes);
        return new Result(fatorR, target, threshold, status,
                new Suggestion(missing, currentProLabore, suggested, dasIII, dasV, extraTaxes, savings));
    }
}
