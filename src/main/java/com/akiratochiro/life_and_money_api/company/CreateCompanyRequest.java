package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCompanyRequest(
        @NotBlank(message = "Informe o nome da empresa.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotBlank(message = "Escolha um emoji para a empresa.")
        String emoji,

        @Schema(description = "Opcional; com ou sem pontuação, numérico ou alfanumérico", example = "12.ABC.345/01DE-35")
        String cnpj,

        @NotNull(message = "Informe a data de abertura.")
        LocalDate openingDate,

        @NotNull(message = "Escolha a natureza jurídica.")
        LegalNature legalNature,

        @NotNull(message = "Escolha o regime tributário.")
        TaxRegime taxRegime,

        @Schema(description = "Obrigatório no Simples Nacional, ausente nos outros regimes")
        AnnexConfig annexConfig,

        @Schema(description = "Obrigatória no MEI, ausente nas outras naturezas")
        MeiActivity meiActivity,

        @Schema(description = "Fração; sem ela, 0.28. Serve só para alertas: o Anexo é decidido pelo limite legal",
                example = "0.28")
        @DecimalMin(value = "0", inclusive = false, message = "A meta do Fator R precisa ser maior que zero.")
        @DecimalMax(value = "1", message = "A meta do Fator R não pode passar de 100%.")
        @Digits(integer = 1, fraction = 4, message = "Use no máximo duas casas decimais no percentual.")
        BigDecimal fatorRTarget,

        @Schema(description = "Saldo da conta PJ na data do saldo; sem ele, zero. Pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "Valor inválido.")
        BigDecimal openingBalance,

        @Schema(description = "Sem ela, hoje. Não pode ser anterior à abertura nem estar no futuro")
        LocalDate openingBalanceDate,

        @Schema(description = "Nome do sócio que é você; sem ele, o nome da sua conta")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String partnerName
) {
    NewCompany toNewCompany() {
        return new NewCompany(name, emoji, cnpj, openingDate, legalNature, taxRegime, annexConfig, meiActivity,
                fatorRTarget, openingBalance, openingBalanceDate, partnerName);
    }
}
