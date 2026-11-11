package com.akiratochiro.life_and_money_api.taxengine;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * O Fator R: folha de salários ÷ receita bruta, nos mesmos meses do RBT12 (LC 123, art. 18, §§5º-J,
 * 5º-K e 5º-M). Igual ou acima do limite legal (28%), Anexo III; abaixo, Anexo V. Quem tem menos de
 * 12 meses tem a folha proporcionalizada como a receita; como as duas usam a mesma janela, a
 * proporção é a razão das somas.
 *
 * <p>Sem receita ou sem folha, valem os valores fixos da Res. CGSN 140, art. 26, §§6º e 7º
 * (conferido no texto oficial em 11/11/2026): folha sem receita é 0,28 (Anexo
 * III); receita sem folha, ou nenhuma das duas, é 0,01 (Anexo V).
 *
 * <p>A folha é o que o chamador soma: pró-labores e despesas das categorias marcadas como folha
 * (salários, encargos, FGTS). A CPP não entra, porque no Anexo III e no V ela está dentro do DAS
 * (plano da Fase 4, item 1.5.3).
 */
public final class FatorR {

    static final BigDecimal NO_PAYROLL = new BigDecimal("0.01");
    static final BigDecimal NO_REVENUE = new BigDecimal("0.28");

    public record Result(BigDecimal payroll, BigDecimal revenue, BigDecimal ratio, Annex annex, Rbt12.Window window) {
    }

    private FatorR() {
    }

    public static Result of(YearMonth competence, YearMonth activityStart, Map<YearMonth, BigDecimal> payrollByMonth,
                            Map<YearMonth, BigDecimal> revenueByMonth, TaxTable table) {
        Rbt12.Window window = Rbt12.window(competence, activityStart);
        BigDecimal payroll = Rbt12.sum(window, payrollByMonth);
        BigDecimal revenue = Rbt12.sum(window, revenueByMonth);
        BigDecimal ratio = ratio(payroll, revenue);
        Annex annex = ratio.compareTo(table.get(TaxParameter.FATOR_R_THRESHOLD)) >= 0 ? Annex.III : Annex.V;
        return new Result(payroll, revenue, ratio, annex, window);
    }

    static BigDecimal ratio(BigDecimal payroll, BigDecimal revenue) {
        boolean hasPayroll = payroll.signum() > 0;
        boolean hasRevenue = revenue.signum() > 0;
        if (hasPayroll && !hasRevenue) {
            return NO_REVENUE;
        }
        if (!hasPayroll) {
            return NO_PAYROLL;
        }
        return payroll.divide(revenue, Money.RATE);
    }
}
