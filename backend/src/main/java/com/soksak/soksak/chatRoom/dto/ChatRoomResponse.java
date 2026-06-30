package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.chatRoom.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String title,
        Long characterId,
        boolean writingToggle,
        boolean foldSpoilerToggle,
        LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getTitle(),
                chatRoom.getCharacter().getId(),
                chatRoom.isWritingToggle(),
                chatRoom.isFoldSpoilerToggle(),
                chatRoom.getCreatedAt()
        );
    }
}
