package com.akiratochiro.life_and_money_api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Consulta a <a href="https://haveibeenpwned.com/API/v3#PwnedPasswords">Pwned Passwords</a>, do
 * Have I Been Pwned: gratuita, sem chave e sem limite de requisições.
 *
 * <p><b>A senha não sai daqui.</b> Pelo modelo de k-anonimato, só os 5 primeiros caracteres do
 * SHA-1 dela vão na requisição; a resposta traz centenas de finais de hash com esse começo, e a
 * comparação com o final certo acontece aqui. O {@code Add-Padding} faz a resposta ter sempre um
 * tamanho parecido, para nem o tamanho dela revelar algo sobre o prefixo (as linhas de enchimento
 * vêm com contagem 0 e são ignoradas).
 *
 * <p>SHA-1 aqui não protege nada: é só o formato em que a base é indexada. A senha continua guardada
 * com BCrypt.
 *
 * <p><b>Falha aberta:</b> se a consulta falhar ou demorar, a senha é aceita. Ficar sem cadastro e
 * sem redefinição de senha porque um serviço de fora caiu seria pior do que aceitar, nesse
 * intervalo, uma senha que a lista recusaria. As outras regras (tamanho) continuam valendo.
 */
class PwnedPasswordsClient implements BreachedPasswords {

    private static final Logger log = LoggerFactory.getLogger(PwnedPasswordsClient.class);
    private static final int PREFIX_LENGTH = 5;

    private final RestClient restClient;

    PwnedPasswordsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isBreached(String password) {
        String hash = sha1(password);
        String prefix = hash.substring(0, PREFIX_LENGTH);
        String suffix = hash.substring(PREFIX_LENGTH);

        String body;
        try {
            body = restClient.get()
                    .uri("/range/{prefix}", prefix)
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("Pwned Passwords indisponível; a senha foi aceita sem essa checagem", e);
            return false;
        }
        return body != null && body.lines().anyMatch(line -> matches(line, suffix));
    }

    /** Cada linha é {@code FINAL_DO_HASH:CONTAGEM}; contagem 0 é enchimento. */
    private static boolean matches(String line, String suffix) {
        int colon = line.indexOf(':');
        if (colon < 0 || !line.substring(0, colon).equalsIgnoreCase(suffix)) {
            return false;
        }
        try {
            return Long.parseLong(line.substring(colon + 1).strip()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 indisponível nesta JVM", e);
        }
    }
}
