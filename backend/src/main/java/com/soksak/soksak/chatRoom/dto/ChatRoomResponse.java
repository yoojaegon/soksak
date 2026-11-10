package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.chatRoom.ChatRoom;

import java.time.LocalDateTime;

// model·thinkingLevel은 이 방에 실제로 적용되는 값이다(방에 고정한 값, 없으면 사용자 기본값).
// 상속 규칙은 서버만 알고, 프론트는 이 값과 followsDefault 플래그만 보고 그린다.
public record ChatRoomResponse(
        Long id,
        String title,
        Long characterId,
        boolean writingToggle,
        boolean foldSpoilerToggle,
        // 카탈로그에서 빠진 모델이면 발송처럼 카탈로그 기본 모델로 보여준다.
        String model,
        // true면 방에 고정한 값이 없어 사용자 기본값을 따르는 중이다.
        boolean modelFollowsDefault,
        // 상속까지만 채운 값이다. 실제로 보내는 값은 모델에 맞게 보정되지만(ModelCatalog.resolveThinking)
        // 그 보정본을 돌려주면 사용자가 고른 적 없는 값이 픽커에 박혀 저장돼 버린다.
        // 보이는 건 고른 것, 쓰이는 건 보정된 것.
        ThinkingLevel thinkingLevel,
        boolean thinkingFollowsDefault,
        LocalDateTime createdAt
) {
    // user를 읽으므로 user가 로딩된 방이어야 한다.
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getTitle(),
                chatRoom.getCharacter().getId(),
                chatRoom.isWritingToggle(),
                chatRoom.isFoldSpoilerToggle(),
                ModelCatalog.displayed(chatRoom.effectiveModel()),
                chatRoom.getModel() == null,
                chatRoom.effectiveThinkingLevel(),
                chatRoom.getThinkingLevel() == null,
                chatRoom.getCreatedAt()
        );
    }
}
