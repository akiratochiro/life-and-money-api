package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Envia o link de redefinição <b>depois do commit</b>: o e-mail só sai se o token foi de fato
 * gravado, já que um link para um token que sofreu rollback não funcionaria. Já roda fora da
 * requisição, na thread do {@link PasswordResetQueue}.
 *
 * <p>Uma falha no envio fica só no log: a resposta já foi dada, e o usuário pode pedir de novo.
 */
@Component
class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final EmailSender emailSender;
    private final String frontUrl;

    PasswordResetEmailListener(EmailSender emailSender, @Value("${app.front-url}") String frontUrl) {
        this.emailSender = emailSender;
        this.frontUrl = frontUrl;
    }

    @TransactionalEventListener
    void on(PasswordResetRequested event) {
        String link = UriComponentsBuilder.fromUriString(frontUrl)
                .path("/reset-password")
                .queryParam("token", event.token())
                .build()
                .toUriString();

        try {
            emailSender.send(PasswordResetEmail.compose(event.email(), event.name(), link));
        } catch (RuntimeException e) {
            // Sem o token e sem o e-mail no log: o id basta para investigar.
            log.error("Falha ao enviar o e-mail de redefinição de senha do usuário {}", event.userId(), e);
        }
    }
}
