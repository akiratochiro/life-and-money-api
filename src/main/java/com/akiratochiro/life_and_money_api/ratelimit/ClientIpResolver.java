package com.akiratochiro.life_and_money_api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Descobre de que IP veio a requisição, para os limites de tentativas.
 *
 * <p>Nenhum cabeçalho padrão serve sozinho aqui:
 * <ul>
 *   <li>{@code X-Forwarded-For} é falsificável: o Render não filtra o que o cliente manda, só
 *       acrescenta o IP do proxy no fim da lista;
 *   <li>{@code CF-Connecting-IP} não é falsificável (a Cloudflare, que fica na frente do Render,
 *       sobrescreve o valor), mas as requisições do app passam pelo Worker do front, que também
 *       roda na Cloudflare. Numa chamada de um Worker para outra zona da Cloudflare, esse cabeçalho
 *       chega com um endereço fixo da Cloudflare, igual para todo mundo.
 * </ul>
 *
 * <p>Então o Worker repassa o IP que ele recebeu em {@code X-Client-IP} e prova que é ele com um
 * segredo em {@code X-Proxy-Auth}. A ordem de confiança é:
 * <ol>
 *   <li>{@code X-Client-IP}, só se o segredo bater;
 *   <li>{@code CF-Connecting-IP}: quem chama o Render direto, sem passar pelo Worker;
 *   <li>o endereço da conexão: desenvolvimento e testes, sem proxy nenhum.
 * </ol>
 *
 * <p>Por isso o {@code server.forward-headers-strategy} fica em {@code none}: as outras estratégias
 * fariam o Spring confiar no {@code X-Forwarded-For}.
 */
@Component
public class ClientIpResolver {

    static final String CLIENT_IP_HEADER = "X-Client-IP";
    static final String PROXY_AUTH_HEADER = "X-Proxy-Auth";
    static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    /** Só dígitos, hexadecimais, ':' e '.': nada que pudesse virar um nome de host. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final byte[] proxySecret;

    public ClientIpResolver(@Value("${app.client-ip.proxy-secret}") String proxySecret) {
        if (proxySecret == null || proxySecret.isBlank()) {
            throw new IllegalStateException("app.client-ip.proxy-secret é obrigatória: sem ela o IP "
                    + "repassado pelo Worker não seria aceito, e todos os usuários dividiriam um só limite");
        }
        this.proxySecret = proxySecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * O IP em forma de chave: IPv4 como está; IPv6 reduzido ao prefixo /64, porque quem tem um
     * bloco IPv6 troca de endereço dentro dele à vontade.
     */
    public String resolve(HttpServletRequest request) {
        if (fromTrustedProxy(request)) {
            InetAddress forwarded = parse(request.getHeader(CLIENT_IP_HEADER));
            if (forwarded != null) {
                return key(forwarded);
            }
        }

        InetAddress cloudflare = parse(request.getHeader(CF_CONNECTING_IP_HEADER));
        if (cloudflare != null) {
            return key(cloudflare);
        }

        InetAddress connection = parse(request.getRemoteAddr());
        return connection != null ? key(connection) : request.getRemoteAddr();
    }

    private boolean fromTrustedProxy(HttpServletRequest request) {
        String presented = request.getHeader(PROXY_AUTH_HEADER);
        // Comparação em tempo constante: o tempo da resposta não pode ir revelando o segredo.
        return presented != null
                && MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), proxySecret);
    }

    /** {@code ofLiteral} não faz consulta DNS: um valor que não é IP é só descartado. */
    private static InetAddress parse(String value) {
        if (value == null || !IP_LITERAL.matcher(value.strip()).matches()) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String key(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
            }
            return prefix.append(":/64").toString();
        }
        return address.getHostAddress();
    }
}
