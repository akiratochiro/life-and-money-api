package com.akiratochiro.life_and_money_api.config;

import com.akiratochiro.life_and_money_api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O decoder confere o emissor, não só a assinatura: um token assinado com a chave certa, mas
 * emitido para outro fim, não abre sessão.
 */
class JwtIssuerIntegrationTest extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void aTokenFromAnotherIssuerIsRejectedEvenWithAValidSignature() throws Exception {
        Long userId = register("emissor.outro@test.dev");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, "outro-sistema")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutIssuerIsRejected() throws Exception {
        Long userId = register("emissor.nenhum@test.dev");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromThisApiIsAccepted() throws Exception {
        Long userId = register("emissor.certo@test.dev");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, JwtConfig.ISSUER)))
                .andExpect(status().isOk());
    }

    private String token(Long userId, String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .subject(userId.toString());
        if (issuer != null) {
            claims.issuer(issuer);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
