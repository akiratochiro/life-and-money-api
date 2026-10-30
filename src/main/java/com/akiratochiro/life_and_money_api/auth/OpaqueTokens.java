package com.akiratochiro.life_and_money_api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos: valores aleatórios que só fazem sentido para o servidor. O valor em claro vai
 * para o usuário (num cookie ou num link) e o banco guarda só o SHA-256 dele, como se faz com a
 * senha: um dump do banco não entrega nada utilizável.
 *
 * <p>SHA-256 sem salt, e não BCrypt, porque o valor já tem 256 bits de entropia: não há
 * dicionário para atacar, e o hash precisa ser determinístico para ser buscado no banco.
 */
final class OpaqueTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {
    }

    /** 256 bits aleatórios em base64url, sem padding: 43 caracteres seguros para URL e cookie. */
    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal: 64 caracteres. */
    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em qualquer JVM; se faltar, não há como seguir.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
