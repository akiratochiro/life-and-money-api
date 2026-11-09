package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.config.JwtConfig;
import com.akiratochiro.life_and_money_api.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    TokenService(JwtEncoder jwtEncoder, @Value("${jwt.expiration}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    TokenResponse generateToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .subject(user.getId().toString())
                .claim(CredentialsVersionValidator.CLAIM, user.getCredentialsVersion())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiration.toSeconds());
    }
}