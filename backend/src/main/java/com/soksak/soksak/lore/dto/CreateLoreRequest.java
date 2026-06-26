package com.soksak.soksak.lore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateLoreRequest(
        @NotBlank String title,
        String keys,
        @NotBlank String content,
        boolean alwaysOn,
        @Min(1) int priority
) {
}
