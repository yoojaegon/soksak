package com.soksak.soksak.lore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLoreRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 50, message = "제목은 50자 이하여야 합니다.")
        String title,
        @Size(max = 200, message = "키워드는 200자 이하여야 합니다.")
        String keys,
        // 내용은 프롬프트에 그대로 들어가서 길이가 곧 토큰이다. 캐릭터 페르소나와 같은 한도로 둔다.
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "내용은 1000자 이하여야 합니다.")
        String content,
        boolean alwaysOn,
        @Min(value = 1, message = "우선순위는 1 이상이어야 합니다.") int priority
) {
}
