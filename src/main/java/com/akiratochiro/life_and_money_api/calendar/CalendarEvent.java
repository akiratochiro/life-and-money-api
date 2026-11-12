package com.akiratochiro.life_and_money_api.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Um vencimento no calendário (decisão 31), vindo de qualquer {@link CalendarEventSource}. Status, prazo e
 * valores chegam prontos da feature dona: o calendário não recalcula nada.
 */
public record CalendarEvent(
        @Schema(requiredMode = REQUIRED, description = "fonte:chave, estável", example = "TAX:12:2026-10") String id,
        @Schema(requiredMode = REQUIRED) Source source,
        @Schema(requiredMode = REQUIRED) Scope scope,
        @Schema(description = "Ausente no PF") CalendarCompany company,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(description = "Competência, \"A receber\", \"Despesa fixa\"...") String detail,
        @Schema(requiredMode = REQUIRED) LocalDate dueDate,
        @Schema(requiredMode = REQUIRED, description = "O último dia sem atraso; o OVERDUE sai dele") LocalDate payableUntil,
        @Schema(requiredMode = REQUIRED) Status status,
        @Schema(requiredMode = REQUIRED) Direction direction,
        @Schema(description = "Ausente quando o app não calcula, ou na declaração") BigDecimal expectedAmount,
        @Schema(description = "Só quando pago") BigDecimal paidAmount,
        @Schema(description = "Só quando pago (ou entregue, na declaração)") LocalDate paidOn,
        @Schema(requiredMode = REQUIRED, description = "DAS de R$ 0,00: aparece apagado e não vira lembrete") boolean nothingToPay,
        @Schema(requiredMode = REQUIRED) CalendarLink link
) {
    public enum Source { TAX, DECLARATION, COMPANY_EXPENSE, BILL }

    public enum Scope { PF, PJ }

    public enum Status { PENDING, PAID, OVERDUE }

    /** OUT é a pagar; IN é a receber (só contas pessoais). */
    public enum Direction { OUT, IN }

    public record CalendarCompany(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String emoji
    ) {
    }

    /** Para onde o toque leva: a tela de cada coisa, onde ela é paga. */
    public record CalendarLink(
            @Schema(requiredMode = REQUIRED) LinkKind kind,
            Long companyId,
            @Schema(requiredMode = REQUIRED, example = "2026-11") YearMonth month
    ) {
    }

    public enum LinkKind { COMPANY_TAXES, COMPANY_EXPENSES, BILLS }

    /** Ainda pede atenção: não pago e com algo a pagar. */
    boolean open() {
        return status != Status.PAID && !nothingToPay;
    }
}
