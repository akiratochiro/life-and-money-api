package com.akiratochiro.life_and_money_api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Tira da requisição o trabalho do cadastro que depende de o e-mail já ter conta: consultar,
 * gravar e mandar o e-mail. Mesma razão e mesmo formato do {@link PasswordResetQueue}.
 */
@Component
class RegistrationQueue {

    private static final Logger log = LoggerFactory.getLogger(RegistrationQueue.class);

    private final RegistrationService registrationService;

    RegistrationQueue(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Async
    void enqueue(String name, String email, String passwordHash) {
        try {
            registrationService.requestRegistration(name, email, passwordHash);
        } catch (RuntimeException e) {
            // A resposta já foi dada; sem o e-mail no log, que é dado pessoal.
            log.error("Falha ao registrar um pedido de cadastro", e);
        }
    }
}
