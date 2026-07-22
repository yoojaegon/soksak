package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

// null/빈값은 받지 않는다. room.model = null은 "아직 안 고름"(resolve()가 기본값으로 폴백)이라는
// 서버 초기 상태 전용이라, 이 제약을 풀면 빈 본문이 사용자의 선택을 조용히 지운다.
public record UpdateModelRequest (
        @NotBlank String model
) {
}
