package com.akiratochiro.life_and_money_api.auth;

import org.springframework.stereotype.Component;

/**
 * As regras de senha que dependem de algo de fora. As que não dependem (tamanho) ficam no
 * {@link ValidPassword}, na validação do corpo. Esta roda depois dela, então uma senha curta nem
 * chega a ser consultada.
 */
@Component
class PasswordPolicy {

    private final BreachedPasswords breachedPasswords;

    PasswordPolicy(BreachedPasswords breachedPasswords) {
        this.breachedPasswords = breachedPasswords;
    }

    /**
     * @param field o nome do campo no corpo da requisição, para o erro marcar o campo certo
     * @throws BreachedPasswordException se a senha está numa lista de senhas vazadas
     */
    void requireNotBreached(String password, String field) {
        if (breachedPasswords.isBreached(password)) {
            throw new BreachedPasswordException(field);
        }
    }
}
