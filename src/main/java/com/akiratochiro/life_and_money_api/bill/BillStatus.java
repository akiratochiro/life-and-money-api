package com.akiratochiro.life_and_money_api.bill;

import java.time.LocalDate;

/** Calculado na leitura, nunca gravado: "atrasada" muda sozinha com a passagem do tempo. */
public enum BillStatus {
    PENDING,
    PAID,
    OVERDUE;

    /** Uma conta que vence hoje ainda está pendente: só atrasa no dia seguinte. */
    public static BillStatus of(boolean paid, LocalDate dueDate, LocalDate today) {
        if (paid) {
            return PAID;
        }
        return dueDate.isBefore(today) ? OVERDUE : PENDING;
    }
}
