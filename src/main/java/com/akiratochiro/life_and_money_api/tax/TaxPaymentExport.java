package com.akiratochiro.life_and_money_api.tax;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Um pagamento no export da conta. O valor e a data pagos estão na despesa ligada. */
public record TaxPaymentExport(
        @Schema(requiredMode = REQUIRED) Long obligationId,
        @Schema(requiredMode = REQUIRED) YearMonth competence,
        BigDecimal expectedAmount,
        Long expenseId,
        LocalDate deliveredOn
) {
    static TaxPaymentExport from(TaxPayment p) {
        return new TaxPaymentExport(p.getObligationId(), p.getCompetenceMonth(), p.getExpectedAmount(), p.getExpenseId(),
                p.getDeliveredOn());
    }
}
