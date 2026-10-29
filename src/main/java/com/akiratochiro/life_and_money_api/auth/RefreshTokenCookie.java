package com.akiratochiro.life_and_money_api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Monta o cookie do refresh token.
 *
 * <p>Os atributos são o ponto da mudança:
 * <ul>
 *   <li><b>HttpOnly</b>: o JavaScript da página não lê o cookie, então um XSS não leva a sessão
 *       inteira embora — era o risco de guardar o token no sessionStorage;
 *   <li><b>Path=/api/auth</b>: o cookie só viaja nas chamadas de autenticação, e não em toda
 *       requisição da API. O prefixo vem do context-path, porque o Path é comparado com o
 *       caminho que o navegador vê, e não com o mapeamento do controller;
 *   <li><b>Secure</b> e <b>SameSite</b>: vêm da configuração, porque dependem de onde o app está.
 *       Em produção o front e a API ficam em domínios diferentes, o que exige
 *       {@code SameSite=None}, e {@code None} só é aceito com {@code Secure}. No localhost, em
 *       http, o par que funciona é {@code Lax} sem {@code Secure}.
 * </ul>
 */
@Component
class RefreshTokenCookie {

    static final String NAME = "refreshToken";

    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final Duration lifetime;

    RefreshTokenCookie(@Value("${server.servlet.context-path:}") String contextPath,
                       @Value("${app.auth.refresh-cookie.secure}") boolean secure,
                       @Value("${app.auth.refresh-cookie.same-site}") String sameSite,
                       @Value("${jwt.refresh-expiration}") Duration lifetime) {
        this.path = contextPath + "/auth";
        this.secure = secure;
        this.sameSite = sameSite;
        this.lifetime = lifetime;
    }

    ResponseCookie of(String value) {
        return base(value).maxAge(lifetime).build();
    }

    /** Cookie vazio e já expirado: é assim que se apaga um cookie no navegador. */
    ResponseCookie expired() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}
