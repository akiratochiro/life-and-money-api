package com.akiratochiro.life_and_money_api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record TokenResponse(
        @Schema(requiredMode = REQUIRED) String accessToken,
        @Schema(requiredMode = REQUIRED) String tokenType,
        @Schema(requiredMode = REQUIRED, description = "Validade do access token, em segundos") long expiresIn
) {
}
