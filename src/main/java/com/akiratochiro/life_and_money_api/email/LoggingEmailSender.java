package com.akiratochiro.life_and_money_api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Para o desenvolvimento: em vez de enviar, escreve no log o destinatário, o assunto e o texto —
 * que é onde está o link. Nunca deve rodar em produção, porque o log guardaria links válidos.
 */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("E-mail não enviado (provedor 'log') para {} — {}\n{}",
                message.to(), message.subject(), message.text());
    }
}
