package com.akiratochiro.life_and_money_api.invoice;

/**
 * O arquivo não pôde ser lido como uma nota. A mensagem vai para o usuário, na pré-visualização
 * daquele arquivo, então é em português e diz o que fazer.
 */
public class InvoiceParseException extends RuntimeException {
    public InvoiceParseException(String message) {
        super(message);
    }

    public InvoiceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
