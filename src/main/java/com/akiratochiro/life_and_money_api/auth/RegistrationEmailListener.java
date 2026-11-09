package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Envia o e-mail do cadastro <b>depois do commit</b>, como o {@link PasswordResetEmailListener}: um
 * link para um cadastro que sofreu rollback não funcionaria.
 */
@Component
class RegistrationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrationEmailListener.class);

    private final EmailSender emailSender;
    private final String frontUrl;

    RegistrationEmailListener(EmailSender emailSender, @Value("${app.front-url}") String frontUrl) {
        this.emailSender = emailSender;
        this.frontUrl = frontUrl;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RegistrationRequested event) {
        try {
            if (event.accountAlreadyExists()) {
                emailSender.send(RegistrationEmails.alreadyRegistered(event.email(), link("/forgot-password", null)));
            } else {
                emailSender.send(RegistrationEmails.confirmation(event.email(), event.name(),
                        link("/confirm-email", event.token())));
            }
        } catch (RuntimeException e) {
            // Sem o token e sem o e-mail no log.
            log.error("Falha ao enviar o e-mail de cadastro", e);
        }
    }

    private String link(String path, String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontUrl).path(path);
        if (token != null) {
            builder.queryParam("token", token);
        }
        return builder.build().toUriString();
    }
}
