package com.akiratochiro.life_and_money_api.bill;

import java.time.LocalDate;

/** Calculado na leitura, nunca gravado: "atrasada" muda sozinha com a passagem do tempo. */
public enum BillStatus {
    PENDING,
    PAID,
    OVERDUE;

    /**
     * Só atrasa no dia seguinte ao último dia para pagar, que é o próprio vencimento ou, se ele
     * cair num fim de semana ou feriado nacional, o próximo dia útil.
     */
    public static BillStatus of(boolean paid, LocalDate payableUntil, LocalDate today) {
        if (paid) {
            return PAID;
        }
        return payableUntil.isBefore(today) ? OVERDUE : PENDING;
    }
}
