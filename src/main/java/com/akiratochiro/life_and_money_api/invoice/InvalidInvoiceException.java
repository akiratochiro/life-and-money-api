package com.akiratochiro.life_and_money_api.invoice;

/** Uma nota que a regra recusa: de outro CNPJ, de homologação, de antes da abertura, de valor do XML alterado. */
public class InvalidInvoiceException extends RuntimeException {
    public InvalidInvoiceException(String message) {
        super(message);
    }
}
