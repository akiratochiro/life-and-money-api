package com.akiratochiro.life_and_money_api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

@Service
public class UserService {
   private final UserRepository userRepository;
   private final PasswordEncoder passwordEncoder;
   private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = User.normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyInUseException();
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = userRepository.save(new User(name, normalizedEmail, passwordHash));

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
        return user;
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return user;
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
    }

    @Transactional
    public void changePassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.changePasswordHash(passwordEncoder.encode(rawPassword));
    }




}
