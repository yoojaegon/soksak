package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.chatRoom.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String title,
        Long characterId,
        boolean writingToggle,
        boolean foldSpoilerToggle,
        String model,
        // 방에 저장된 값 그대로(null = 아직 안 고름). 실제로 보내는 값은 모델에 맞게 보정되지만
        // (ModelCatalog.resolveThinking) 그 보정본을 돌려주면 사용자가 고른 적 없는 값이
        // 픽커에 박혀 저장돼 버린다 — 보이는 건 고른 것, 쓰이는 건 보정된 것.
        ThinkingLevel thinkingLevel,
        LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getTitle(),
                chatRoom.getCharacter().getId(),
                chatRoom.isWritingToggle(),
                chatRoom.isFoldSpoilerToggle(),
                ModelCatalog.orNull(chatRoom.getModel()),
                chatRoom.getThinkingLevel(),
                chatRoom.getCreatedAt()
        );
    }
}
