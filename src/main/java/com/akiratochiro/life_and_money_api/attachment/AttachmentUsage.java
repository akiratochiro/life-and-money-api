package com.akiratochiro.life_and_money_api.attachment;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record AttachmentUsage(
        @Schema(requiredMode = REQUIRED) long usedBytes,
        @Schema(requiredMode = REQUIRED) long limitBytes
) {
}
