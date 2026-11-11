package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.taxengine.Annex;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A memória de cálculo de um imposto previsto: o que a tela mostra para o usuário entender (e
 * conferir com o contador) de onde veio o número. Só uma das três partes vem preenchida.
 */
public record TaxCalculation(
        @Schema(description = "DAS do Simples Nacional") Simples simples,
        @Schema(description = "DAS-MEI") Simei simei,
        @Schema(description = "DARF do pró-labore") Darf darf
) {
    public record Simples(
            @Schema(requiredMode = REQUIRED) BigDecimal revenue,
            @Schema(requiredMode = REQUIRED) BigDecimal revenueWithIssWithheld,
            @Schema(requiredMode = REQUIRED) BigDecimal rbt12,
            @Schema(requiredMode = REQUIRED, description = "FIRST_MONTH, PROPORTIONAL ou TWELVE_MONTHS") String rbt12Method,
            @Schema(requiredMode = REQUIRED) YearMonth windowFrom,
            @Schema(requiredMode = REQUIRED) YearMonth windowTo,
            @Schema(requiredMode = REQUIRED) Annex annex,
            @Schema(description = "Só quando o Anexo sai do Fator R") BigDecimal fatorR,
            @Schema(description = "A folha da janela, quando há Fator R") BigDecimal payroll,
            @Schema(requiredMode = REQUIRED) int bracket,
            @Schema(requiredMode = REQUIRED) BigDecimal nominalRate,
            @Schema(requiredMode = REQUIRED) BigDecimal deduction,
            @Schema(requiredMode = REQUIRED) BigDecimal effectiveRate,
            @Schema(requiredMode = REQUIRED) BigDecimal issRate
    ) {
    }

    public record Simei(
            @Schema(requiredMode = REQUIRED) BigDecimal inss,
            @Schema(requiredMode = REQUIRED) BigDecimal icms,
            @Schema(requiredMode = REQUIRED) BigDecimal iss
    ) {
    }

    public record Darf(@Schema(requiredMode = REQUIRED) List<ProLaboreLine> proLabores) {
    }

    public record ProLaboreLine(
            @Schema(requiredMode = REQUIRED) Long partnerId,
            @Schema(requiredMode = REQUIRED) String partnerName,
            @Schema(requiredMode = REQUIRED) BigDecimal gross,
            @Schema(requiredMode = REQUIRED) BigDecimal inss,
            @Schema(requiredMode = REQUIRED) BigDecimal irrf,
            @Schema(requiredMode = REQUIRED, description = "Falso quando é o pró-labore planejado, ainda não registrado")
            boolean registered
    ) {
    }
}
