package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.aiClient.ThinkingLevel;
import jakarta.validation.constraints.NotNull;

// UpdateModelRequest와 같은 이유로 null을 받지 않는다. room.thinkingLevel = null은
// "아직 안 고름"이라는 서버 초기 상태 전용이라, 이 제약을 풀면 빈 본문 {}가 사용자의 선택을
// 200 OK와 함께 조용히 지운다.
public record UpdateThinkingRequest(
        @NotNull ThinkingLevel thinkingLevel
) {
}
