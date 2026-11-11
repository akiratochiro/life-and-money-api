package com.akiratochiro.life_and_money_api.invoice;

/**
 * De onde vieram os valores. Por enquanto só o XML entra; {@code PDF} e {@code MANUAL} continuam no
 * enum e no {@code CHECK} do banco para voltar a aceitar PDF sem migration.
 */
public enum InvoiceSource {
    XML,
    PDF,
    MANUAL
}
