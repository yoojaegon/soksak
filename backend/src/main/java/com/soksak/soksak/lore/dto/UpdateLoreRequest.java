package com.soksak.soksak.lore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateLoreRequest(
        @NotBlank(message = "제목은 필수입니다.") String title,
        String keys,
        @NotBlank(message = "내용은 필수입니다.") String content,
        boolean alwaysOn,
        @Min(value = 1, message = "우선순위는 1 이상이어야 합니다.") int priority
) {
}
