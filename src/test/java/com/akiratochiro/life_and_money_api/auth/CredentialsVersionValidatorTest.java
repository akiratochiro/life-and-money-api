package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.CredentialsVersions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialsVersionValidatorTest {

    private final CredentialsVersions versions = mock(CredentialsVersions.class);
    private final CredentialsVersionValidator validator = new CredentialsVersionValidator(versions);

    @Test
    void aTokenFromTheCurrentVersionIsAccepted() {
        when(versions.current(7L)).thenReturn(Optional.of(2));

        assertThat(validator.validate(token("7", 2)).hasErrors()).isFalse();
    }

    @Test
    void aTokenFromAnOlderVersionIsRejected() {
        when(versions.current(7L)).thenReturn(Optional.of(3));

        assertThat(validator.validate(token("7", 2)).hasErrors()).isTrue();
    }

    @Test
    void aTokenWithoutTheVersionCountsAsVersionZero() {
        // Os tokens emitidos antes do deploy desta checagem: valem enquanto a senha não mudar.
        when(versions.current(7L)).thenReturn(Optional.of(0));
        assertThat(validator.validate(token("7", null)).hasErrors()).isFalse();

        when(versions.current(7L)).thenReturn(Optional.of(1));
        assertThat(validator.validate(token("7", null)).hasErrors()).isTrue();
    }

    @Test
    void aTokenOfAUserThatNoLongerExistsIsRejected() {
        when(versions.current(7L)).thenReturn(Optional.empty());

        assertThat(validator.validate(token("7", 0)).hasErrors()).isTrue();
    }

    @Test
    void aSubjectThatIsNotAnIdIsRejected() {
        assertThat(validator.validate(token("nao-e-id", 0)).hasErrors()).isTrue();
    }

    private static Jwt token(String subject, Integer version) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now());
        if (version != null) {
            builder.claim(CredentialsVersionValidator.CLAIM, version);
        }
        return builder.build();
    }
}
