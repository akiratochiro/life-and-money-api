package com.akiratochiro.life_and_money_api.email;

/**
 * Envia e-mails sem que quem chama saiba por qual provedor. Em produção é o Brevo; no
 * desenvolvimento e nos testes, uma implementação que só registra a mensagem.
 */
public interface EmailSender {

    /**
     * @throws EmailDeliveryException se o provedor recusar a mensagem ou não responder
     */
    void send(EmailMessage message);
}
