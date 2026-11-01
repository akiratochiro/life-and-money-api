package com.akiratochiro.life_and_money_api.bill;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CreateBillRequest(
        @NotBlank(message = "Informe o nome da conta.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotNull(message = "Escolha uma categoria.")
        @Schema(description = "Categoria de receita (a receber) ou de despesa (a pagar)")
        Long categoryId,

        @NotNull(message = "Informe o valor previsto.")
        @Positive(message = "O valor previsto deve ser maior que zero.")
        @Digits(integer = 17, fraction = 2, message = "O valor deve ter no máximo duas casas decimais.")
        BigDecimal amount,

        @NotNull(message = "Informe o dia de vencimento.")
        @Min(value = 1, message = "O dia de vencimento vai de 1 a 31.")
        @Max(value = 31, message = "O dia de vencimento vai de 1 a 31.")
        @Schema(description = "Num mês sem esse dia, a conta vence no último dia do mês")
        Integer dueDay,

        @NotNull(message = "Escolha a recorrência.")
        BillRecurrence recurrence,

        @NotNull(message = "Informe o primeiro mês.")
        @Schema(example = "2026-11", description = "Primeiro mês; nas anuais, também o mês de vencimento")
        YearMonth startMonth,

        @Schema(example = "2027-10", description = "Último mês, opcional; ignorado nas contas únicas")
        YearMonth endMonth
) {

    /** Validação de dois campos; o {@code @JsonIgnore} só tira o método do corpo no OpenAPI. */
    @JsonIgnore
    @AssertTrue(message = "O último mês não pode ser antes do primeiro.")
    public boolean isPeriodValid() {
        return startMonth == null || endMonth == null || !endMonth.isBefore(startMonth);
    }
}
