package com.soksak.soksak.user.dto;

import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.user.User;

import java.time.LocalDateTime;


public record UserResponse (
        Long id,
        String email,
        String loginId,
        String nickname,
        // 실제로 쓰이는 모델을 보여준다. 저장된 모델이 카탈로그에서 빠졌으면 발송처럼 기본 모델로 본다.
        String defaultModel,
        // 고른 값 그대로다. 모델에 맞춘 보정본을 주면 고른 적 없는 값이 픽커에 박혀 저장된다.
        ThinkingLevel defaultThinkingLevel,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user){
        return new UserResponse(user.getId(), user.getEmail(), user.getLoginId(),
                user.getNickname(), ModelCatalog.resolve(user.getDefaultModel()),
                user.getDefaultThinkingLevel(), user.getCreatedAt());
    }
}
