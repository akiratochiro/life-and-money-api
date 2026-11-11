package com.akiratochiro.life_and_money_api.attachment;

/** Um arquivo recusado: grande demais, de um tipo não aceito ou acima da cota. A mensagem vai para o usuário. */
public class InvalidAttachmentException extends RuntimeException {
    public InvalidAttachmentException(String message) {
        super(message);
    }
}
