package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.User;
import com.akiratochiro.life_and_money_api.user.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cadastro com confirmação de e-mail: a conta só nasce quando o link do e-mail é usado.
 *
 * <p>O pedido de cadastro não revela se o e-mail já tem conta, nem no corpo nem no tempo da
 * resposta: o trabalho que depende disso roda fora da requisição ({@link RegistrationQueue}). Quem
 * já tem conta recebe um aviso por e-mail, em vez de um link; a senha dele não muda.
 */
@Service
class RegistrationService {

    static final Duration VALIDITY = Duration.ofHours(24);

    /**
     * Intervalo mínimo entre dois e-mails para o mesmo endereço, como no reset de senha: sem ele,
     * qualquer um encheria a caixa de outra pessoa e gastaria a cota diária do provedor.
     */
    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final PendingRegistrationRepository pendingRegistrations;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Quando cada dono de conta foi avisado pela última vez. Em memória porque o aviso não grava
     * nada no banco; com uma instância só, o intervalo vale para todo o tráfego.
     */
    private final Cache<String, Instant> ownersNotified = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(MIN_INTERVAL)
            .build();

    RegistrationService(PendingRegistrationRepository pendingRegistrations,
                        UserService userService,
                        ApplicationEventPublisher eventPublisher,
                        Clock clock) {
        this.pendingRegistrations = pendingRegistrations;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Registra o pedido e agenda o e-mail certo. Roda fora da requisição.
     *
     * @param passwordHash a senha já com BCrypt: o hash é calculado na requisição, igual para todo
     *                     mundo, e a senha em claro não precisa viajar até aqui
     */
    @Transactional
    void requestRegistration(String name, String email, String passwordHash) {
        String normalizedEmail = User.normalizeEmail(email);
        Instant now = clock.instant();
        pendingRegistrations.deleteExpired(now);

        if (userService.findByEmail(normalizedEmail).isPresent()) {
            notifyOwner(normalizedEmail, name, now);
            return;
        }

        PendingRegistration pending = pendingRegistrations.findByEmail(normalizedEmail)
                .orElseGet(() -> new PendingRegistration(normalizedEmail));
        if (pending.wasCreatedAfter(now.minus(MIN_INTERVAL))) {
            log.info("Cadastro ignorado: outro pedido para o mesmo e-mail há menos de {} s", MIN_INTERVAL.toSeconds());
            return;
        }

        String token = OpaqueTokens.generate();
        pending.renew(name, passwordHash, OpaqueTokens.hash(token), now, now.plus(VALIDITY));
        pendingRegistrations.save(pending);
        eventPublisher.publishEvent(new RegistrationRequested(normalizedEmail, name, token));
    }

    /**
     * Usa o link: cria a conta com o nome e a senha do cadastro pendente.
     *
     * @throws InvalidConfirmationTokenException se o link não existe, expirou, já foi usado ou foi
     *         trocado por um cadastro mais novo, ou se o e-mail ganhou uma conta nesse meio-tempo
     */
    @Transactional
    User confirm(String token) {
        PendingRegistration pending = pendingRegistrations.findValid(OpaqueTokens.hash(token), clock.instant())
                .orElseThrow(InvalidConfirmationTokenException::new);

        if (pendingRegistrations.consume(pending.getId()) == 0
                || userService.findByEmail(pending.getEmail()).isPresent()) {
            throw new InvalidConfirmationTokenException();
        }
        return userService.createConfirmed(pending.getName(), pending.getEmail(), pending.getPasswordHash());
    }

    private void notifyOwner(String email, String requestedName, Instant now) {
        if (ownersNotified.asMap().putIfAbsent(email, now) != null) {
            log.info("Aviso de cadastro repetido ignorado: o dono foi avisado há menos de {} s",
                    MIN_INTERVAL.toSeconds());
            return;
        }
        eventPublisher.publishEvent(new RegistrationRequested(email, requestedName, null));
    }
}
