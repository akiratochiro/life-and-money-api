package com.akiratochiro.life_and_money_api.invoice;

/** De onde vieram os valores: do XML (travados), do PDF (confirmados pelo usuário) ou digitados. */
public enum InvoiceSource {
    XML,
    PDF,
    MANUAL
}
