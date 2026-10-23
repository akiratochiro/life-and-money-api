package com.akiratochiro.life_and_money_api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

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




}
