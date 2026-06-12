package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateChatRoomRequest(
        @NotBlank String title
) {
}
