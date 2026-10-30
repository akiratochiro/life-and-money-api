package com.akiratochiro.life_and_money_api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Tira da requisição todo o trabalho de um pedido de redefinição: buscar a conta, gravar o token
 * e enviar o e-mail. A requisição só valida o formato do e-mail e responde.
 *
 * <p>Medido, o pedido para um e-mail com conta levava uns 2 ms a mais que para um sem conta — o
 * update, o insert e o commit que só o primeiro faz. Numa requisição isolada isso some no ruído
 * da rede, mas quem mede centenas de vezes separa as duas distribuições e descobre quem tem
 * cadastro. Fora da requisição, o tempo de resposta é o mesmo nos dois casos.
 *
 * <p>É uma classe à parte, e não um método {@code @Async} no próprio serviço, porque o
 * {@code @Async} e o {@code @Transactional} funcionam por proxy: uma chamada de dentro da mesma
 * classe não passaria pelo proxy e rodaria na thread da requisição.
 */
@Component
class PasswordResetQueue {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetQueue.class);

    private final PasswordResetService passwordResetService;

    PasswordResetQueue(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Async
    void enqueue(String email) {
        try {
            passwordResetService.requestReset(email);
        } catch (RuntimeException e) {
            // A resposta já foi dada; sem o e-mail no log, que é dado pessoal.
            log.error("Falha ao registrar um pedido de redefinição de senha", e);
        }
    }
}
