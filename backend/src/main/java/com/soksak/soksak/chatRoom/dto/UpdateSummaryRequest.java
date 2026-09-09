package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotNull;

// 길이 상한은 여기 없다 — 방마다 예산이 달라질 수 있어(A7) 컴파일 상수로는 표현할 수 없고,
// 표시·주입과 같은 자(토큰)로 재야 해서 ChatRoomService.updateSummary의 가드로 옮겼다.
// @NotNull은 남는다: 빈 본문 {}이 200 OK와 함께 사용자의 기억을 조용히 지우면 안 된다.
public record UpdateSummaryRequest(
        @NotNull String summary
) {
}
