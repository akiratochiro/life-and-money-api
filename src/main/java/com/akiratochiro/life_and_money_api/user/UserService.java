package com.akiratochiro.life_and_money_api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
   private final UserRepository userRepository;
   private final PasswordEncoder passwordEncoder;
   private final ApplicationEventPublisher eventPublisher;

    /**
     * Hash de uma senha que ninguém tem, gerado com o mesmo encoder (e o mesmo custo) dos hashes de
     * verdade. O login com e-mail sem conta confere a senha contra ele: assim os dois casos levam o
     * mesmo tempo, e o tempo de resposta não revela quem tem cadastro.
     */
    private final String dummyPasswordHash;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * O BCrypt da senha de um cadastro. Fica separado da criação da conta porque roda na
     * requisição, igual para todo mundo, e a conta só nasce depois, na confirmação do e-mail.
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Cria a conta de um cadastro cujo e-mail acabou de ser confirmado.
     *
     * @throws EmailAlreadyInUseException se o e-mail ganhou uma conta nesse meio-tempo
     */
    @Transactional
    public User createConfirmed(String name, String email, String passwordHash) {
        String normalizedEmail = User.normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyInUseException();
        }

        User user = userRepository.save(new User(name, normalizedEmail, passwordHash));
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
        return user;
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));

        // O BCrypt roda nos dois caminhos, mesmo sem conta: ver dummyPasswordHash.
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);

        if (found.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }

        return found.get();
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Busca sem lançar exceção quando não acha: quem pede a redefinição de senha precisa tratar
     * "não existe" exatamente como "existe", sem que isso vire uma resposta diferente.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email));
    }

    /**
     * Confirma a senha de quem já está logado, antes de uma ação que não tem volta.
     *
     * @throws IncorrectPasswordException se a senha não confere
     */
    @Transactional(readOnly = true)
    public void verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IncorrectPasswordException();
        }
    }

    /**
     * Apaga o usuário. Os dados das features precisam ter sido apagados antes, na mesma transação:
     * as chaves estrangeiras não têm cascata, então um dado esquecido faz esta exclusão falhar em vez
     * de ficar órfão.
     */
    @Transactional
    public void delete(Long userId) {
        userRepository.deleteById(userId);
        eventPublisher.publishEvent(new CredentialsChangedEvent(userId));
    }

    @Transactional
    public void changePassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.changePasswordHash(passwordEncoder.encode(rawPassword));
        eventPublisher.publishEvent(new CredentialsChangedEvent(userId));
    }
}
