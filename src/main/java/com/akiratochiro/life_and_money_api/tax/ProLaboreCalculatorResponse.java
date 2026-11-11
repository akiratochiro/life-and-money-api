package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.taxengine.Annex;
import com.akiratochiro.life_and_money_api.taxengine.ProLaboreAdvisor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A comparação da calculadora: Anexo III com o pró-labore do Fator R, Anexo V com o mínimo. */
public record ProLaboreCalculatorResponse(
        @Schema(requiredMode = REQUIRED) BigDecimal monthlyRevenue,
        @Schema(requiredMode = REQUIRED, description = "A meta usada: a da empresa, nunca abaixo do limite legal") BigDecimal target,
        @Schema(requiredMode = REQUIRED) Scenario annexIII,
        @Schema(requiredMode = REQUIRED) Scenario annexV,
        @Schema(requiredMode = REQUIRED, description = "Custo do V − custo do III; positivo quando o III sai mais barato") BigDecimal savings,
        @Schema(requiredMode = REQUIRED) Annex cheaper,
        @Schema(requiredMode = REQUIRED) String notice
) {
    public record Scenario(
            @Schema(requiredMode = REQUIRED) BigDecimal proLabore,
            @Schema(requiredMode = REQUIRED) BigDecimal das,
            @Schema(requiredMode = REQUIRED) BigDecimal effectiveRate,
            @Schema(requiredMode = REQUIRED) BigDecimal inss,
            @Schema(requiredMode = REQUIRED) BigDecimal irrf,
            @Schema(requiredMode = REQUIRED) BigDecimal netProLabore,
            @Schema(requiredMode = REQUIRED, description = "DAS + INSS + IRRF do mês") BigDecimal totalCost
    ) {
        static Scenario from(ProLaboreAdvisor.Scenario s) {
            return new Scenario(s.proLabore(), s.das().amount(), s.das().effectiveRate(), s.proLaboreTaxes().inss(),
                    s.proLaboreTaxes().irrf(), s.proLaboreTaxes().net(), s.totalCost());
        }
    }

    static ProLaboreCalculatorResponse from(ProLaboreAdvisor.Result result, String notice) {
        return new ProLaboreCalculatorResponse(result.monthlyRevenue(), result.target(), Scenario.from(result.annexIII()),
                Scenario.from(result.annexV()), result.savings(), result.cheaper(), notice);
    }
}
