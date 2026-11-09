package com.akiratochiro.life_and_money_api.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Optional;

/**
 * A versão atual das credenciais de cada usuário, que o validador do access token confere em toda
 * requisição autenticada.
 *
 * <p>Em cache, para não virar uma consulta ao banco por requisição. O cache é apagado <b>depois do
 * commit</b> de uma troca de senha ou de uma exclusão de conta: apagado antes, uma requisição no
 * meio da transação poderia recolocar a versão antiga. Como há uma instância só, a revogação é
 * imediata; a expiração de alguns minutos é só uma rede de segurança.
 */
@Component
public class CredentialsVersions {

    private static final Duration EXPIRATION = Duration.ofMinutes(5);
    private static final long MAX_USERS = 10_000;

    private final UserRepository userRepository;
    private final Cache<Long, Optional<Integer>> versions = Caffeine.newBuilder()
            .maximumSize(MAX_USERS)
            .expireAfterWrite(EXPIRATION)
            .build();

    CredentialsVersions(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** A versão atual, ou vazio se o usuário não existe mais. */
    public Optional<Integer> current(Long userId) {
        return versions.get(userId, userRepository::findCredentialsVersionById);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(CredentialsChangedEvent event) {
        versions.invalidate(event.userId());
    }
}
