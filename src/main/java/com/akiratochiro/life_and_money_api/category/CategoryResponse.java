package com.akiratochiro.life_and_money_api.category;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record CategoryResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) CategoryType type,
        @Schema(requiredMode = REQUIRED) boolean archived,
        @Schema(requiredMode = REQUIRED) Instant createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.isArchived(),
                category.getCreatedAt()
        );
    }
}
