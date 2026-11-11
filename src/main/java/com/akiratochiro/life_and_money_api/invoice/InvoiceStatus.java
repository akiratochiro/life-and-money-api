package com.akiratochiro.life_and_money_api.invoice;

/**
 * Status manual: o cancelamento de uma NFS-e é um evento separado, que nunca muda o XML da nota,
 * então quem sabe que ela foi cancelada é o usuário. Nota cancelada não conta como receita.
 */
public enum InvoiceStatus {
    ATIVA,
    CANCELADA
}
