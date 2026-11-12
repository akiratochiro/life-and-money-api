package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.company.TaxRegime;
import com.akiratochiro.life_and_money_api.taxengine.RevenueLimits;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Os limites de faturamento do ano (decisão 32), com as frases prontas: o front só mostra. */
public record RevenueLimitsResponse(
        @Schema(requiredMode = REQUIRED, description = "Só no MEI e no Simples") boolean applies,
        @Schema(requiredMode = REQUIRED) int year,
        @Schema(requiredMode = REQUIRED, description = "Os anos que dá para ver, do mais recente ao mais antigo") List<Integer> years,
        @Schema(description = "O enquadramento usado: o vigente hoje, ou o de dezembro num ano passado") TaxRegime regime,
        @Schema(requiredMode = REQUIRED) boolean openingYear,
        @Schema(requiredMode = REQUIRED, description = "Meses de atividade no ano (12 fora do ano de abertura)") int activityMonths,
        @Schema(requiredMode = REQUIRED, description = "Notas ativas com competência no ano") BigDecimal revenue,
        @Schema(description = "Ausente num ano fechado") Projection projection,
        @Schema(requiredMode = REQUIRED) List<LimitView> limits,
        @Schema(description = "O limite mais alto que pede atenção; ausente se nenhum") RevenueLimits.Kind alert,
        @Schema(description = "A ressalva da LC 238/2026 nos limites do MEI de 2027 em diante") String conditional,
        @Schema(requiredMode = REQUIRED) String notice
) {
    public enum Basis { EXPECTED_REVENUE, AVERAGE }

    public enum Severity { INFO, WARNING, DANGER }

    public record Projection(
            @Schema(requiredMode = REQUIRED, description = "A receita do ano no ritmo atual") BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Os meses que faltam pelo faturamento esperado, ou pela média") Basis basis
    ) {
    }

    public record LimitView(
            @Schema(requiredMode = REQUIRED) RevenueLimits.Kind kind,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED, description = "Já proporcional no ano de abertura") BigDecimal amount,
            @Schema(description = "Limite + 20%, onde há tolerância") BigDecimal toleranceAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal ratio,
            @Schema(requiredMode = REQUIRED) RevenueLimits.Level level,
            @Schema(description = "Ausente quando não há o que avisar") Severity severity,
            @Schema(description = "O mês em que passou", example = "2026-07") YearMonth exceededIn,
            @Schema(description = "Desde quando a consequência vale", example = "2027-01") YearMonth effectiveFrom,
            @Schema(description = "O mês em que a projeção passa, se ainda não passou", example = "2026-12") YearMonth projectedIn,
            @Schema(description = "O aviso em linguagem simples, a partir de 70%") String message,
            @Schema(description = "\"No ritmo atual, ...\"") String projectionMessage,
            @Schema(requiredMode = REQUIRED) String source
    ) {
    }
}
