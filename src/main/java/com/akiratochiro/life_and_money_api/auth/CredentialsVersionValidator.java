package com.akiratochiro.life_and_money_api.auth;

import com.akiratochiro.life_and_money_api.user.CredentialsVersions;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Recusa o access token emitido antes da última troca de senha, ou de um usuário que não existe
 * mais.
 *
 * <p>O JWT é autocontido: sem esta checagem, depois de redefinir a senha (porque alguém a descobriu)
 * o refresh token era revogado, mas o access token de quem invadiu continuava valendo até vencer,
 * em até 15 minutos. O token leva a versão das credenciais em que foi emitido ({@link #CLAIM}), e
 * ela precisa ser a atual.
 *
 * <p>Token sem a versão conta como versão 0: são os emitidos antes desta checagem existir, e todo
 * usuário começa em 0. Assim o deploy não encerra a sessão de ninguém.
 */
@Component
class CredentialsVersionValidator implements OAuth2TokenValidator<Jwt> {

    static final String CLAIM = "cv";

    private static final OAuth2TokenValidatorResult REVOKED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "A sessão foi encerrada", null));

    private final CredentialsVersions credentialsVersions;

    CredentialsVersionValidator(CredentialsVersions credentialsVersions) {
        this.credentialsVersions = credentialsVersions;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Long userId;
        try {
            userId = Long.valueOf(token.getSubject());
        } catch (NumberFormatException e) {
            return REVOKED;
        }

        Object claim = token.getClaim(CLAIM);
        int tokenVersion = claim instanceof Number number ? number.intValue() : 0;

        return credentialsVersions.current(userId)
                .filter(current -> current == tokenVersion)
                .map(current -> OAuth2TokenValidatorResult.success())
                .orElse(REVOKED);
    }
}
