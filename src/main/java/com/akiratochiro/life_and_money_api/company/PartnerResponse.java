package com.akiratochiro.life_and_money_api.company;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record PartnerResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED, description = "O sócio que é o próprio usuário") boolean me
) {
    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(partner.getId(), partner.getName(), partner.isMe());
    }
}
