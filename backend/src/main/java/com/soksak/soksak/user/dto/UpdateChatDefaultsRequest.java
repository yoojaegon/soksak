package com.soksak.soksak.user.dto;

import com.soksak.soksak.aiClient.ThinkingLevel;

// 사용자 채팅 기본값 부분 수정. 보낸 필드만 바꾸고 null인 필드는 그대로 둔다.
// 사용자 기본값은 늘 값이 있어야 하므로(방이 null일 때 기대는 곳) null로 되돌리는 요청은 없다.
// 필드가 전부 null인 빈 본문은 서비스에서 400으로 막는다.
public record UpdateChatDefaultsRequest(
        // 카탈로그에 있는지는 서비스에서 확인한다.
        String model,

        // 모델과 맞는지는 막지 않는다. 방과 같은 규칙으로, 맞추는 건 발송 직전의 resolveThinking() 몫이다.
        ThinkingLevel thinkingLevel
) {
    public boolean isEmpty() {
        return model == null && thinkingLevel == null;
    }
}
