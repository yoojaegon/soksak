package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotNull;

public record CreateChatRoomRequest(
    @NotNull Long characterId
) {
}
