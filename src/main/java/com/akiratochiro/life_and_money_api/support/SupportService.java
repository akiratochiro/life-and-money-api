package com.akiratochiro.life_and_money_api.support;

import com.akiratochiro.life_and_money_api.email.EmailDeliveryException;
import com.akiratochiro.life_and_money_api.email.EmailSender;
import com.akiratochiro.life_and_money_api.email.PlainEmail;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimit;
import com.akiratochiro.life_and_money_api.ratelimit.RateLimiter;
import com.akiratochiro.life_and_money_api.user.User;
import com.akiratochiro.life_and_money_api.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * O contato com o suporte (decisão 34): a mensagem de quem está logado vai para o endereço de suporte,
 * com o "Responder" apontando para o e-mail da conta. O endereço fica só na configuração
 * ({@code SUPPORT_EMAIL}): nunca aparece no site, no front nem no repositório.
 */
@Service
public class SupportService {

    static final String DEFAULT_SUBJECT = "Mensagem pelo app";

    private final UserService userService;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final String supportEmail;

    public SupportService(UserService userService, EmailSender emailSender, RateLimiter rateLimiter,
                          @Value("${app.support-email}") String supportEmail) {
        this.userService = userService;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.supportEmail = supportEmail;
    }

    /** Envia na hora, para quem escreveu saber se chegou; o limite barra um formulário em laço. */
    public void send(Long userId, SupportRequest request) {
        rateLimiter.consume(RateLimit.SUPPORT_MESSAGE_PER_USER, String.valueOf(userId));
        User user = userService.findById(userId);
        String subject = request.subject() == null || request.subject().isBlank() ? DEFAULT_SUBJECT : request.subject().strip();
        try {
            emailSender.send(PlainEmail.to(supportEmail, "[Suporte] " + subject)
                    .paragraph("Mensagem de " + user.getName() + " (" + user.getEmail() + "), conta nº " + user.getId() + ".")
                    .paragraph("Responder a este e-mail responde direto para quem escreveu.")
                    .userText(request.message())
                    .replyTo(user.getEmail())
                    .build());
        } catch (EmailDeliveryException e) {
            throw new SupportUnavailableException(e);
        }
    }
}
