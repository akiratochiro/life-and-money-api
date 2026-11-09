package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * "Este navegador já entrou nesta conta": o <i>device cookie</i> da OWASP contra o bloqueio de
 * conta por terceiros.
 *
 * <p>O limite de tentativas por e-mail segura quem testa senhas de uma conta a partir de muitos IPs,
 * mas, sozinho, deixa qualquer um travar o login de outra pessoa: bastam algumas senhas erradas com
 * o e-mail dela. Com este cookie, quem já entrou na conta por este navegador sai desse limite e fica
 * num limite próprio, por aparelho. O atacante continua travando só os navegadores desconhecidos.
 *
 * <p>O valor é {@code id.assinatura}: um id aleatório e um HMAC-SHA256 de {@code id:e-mail}. Não há
 * nada no banco: a assinatura prova que o cookie saiu daqui, depois de um login certo, para aquele
 * e-mail. Um cookie de uma conta não vale para outra, e um cookie alterado vale como nenhum.
 *
 * <p>A chave é derivada do {@code jwt.secret} com um rótulo próprio, em vez de ser o mesmo segredo:
 * uma chave por finalidade, sem uma variável de ambiente a mais para configurar no deploy.
 */
@Component
class LoginDeviceCookie {

    static final String NAME = "loginDevice";

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] KEY_LABEL = "life-and-money/login-device-cookie/v1".getBytes(StandardCharsets.UTF_8);
    private static final int ID_BYTES = 16;
    private static final Duration LIFETIME = Duration.ofDays(365);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final String path;
    private final boolean secure;
    private final String sameSite;

    LoginDeviceCookie(@Value("${jwt.secret}") String jwtSecret,
                      @Value("${server.servlet.context-path:}") String contextPath,
                      @Value("${app.auth.refresh-cookie.secure}") boolean secure,
                      @Value("${app.auth.refresh-cookie.same-site}") String sameSite) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), ALGORITHM), KEY_LABEL),
                ALGORITHM);
        // Só o login lê este cookie: ele não viaja nem no refresh.
        this.path = contextPath + "/auth/login";
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /**
     * O id do aparelho, se o cookie foi emitido por esta API para este e-mail. Qualquer outro caso
     * (sem cookie, formato errado, assinatura que não bate, outra conta) é vazio.
     */
    Optional<String> deviceIdFor(String cookieValue, String email) {
        if (cookieValue == null || email == null) {
            return Optional.empty();
        }
        int dot = cookieValue.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String id = cookieValue.substring(0, dot);
        byte[] presented = cookieValue.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = signature(id, email).getBytes(StandardCharsets.US_ASCII);
        // Tempo constante: a resposta não pode ir revelando a assinatura certa byte a byte.
        return MessageDigest.isEqual(presented, expected) ? Optional.of(id) : Optional.empty();
    }

    /**
     * Cookie para o e-mail que acabou de entrar. Mantém o id do aparelho quando ele já tinha um
     * válido (o limite por aparelho continua o mesmo), e renova a validade.
     */
    ResponseCookie issue(String currentValue, String email) {
        String id = deviceIdFor(currentValue, email).orElseGet(LoginDeviceCookie::newId);
        return ResponseCookie.from(NAME, id + "." + signature(id, email))
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(LIFETIME)
                .build();
    }

    private String signature(String id, String email) {
        String payload = id + ":" + User.normalizeEmail(email);
        return ENCODER.encodeToString(hmac(key, payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String newId() {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static byte[] hmac(SecretKeySpec key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 é obrigatório em qualquer JVM; se faltar, não há como seguir.
            throw new IllegalStateException("HmacSHA256 indisponível nesta JVM", e);
        }
    }
}
