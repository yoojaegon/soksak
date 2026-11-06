package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChatRoomRequest(
        // 기본 제목은 캐릭터 이름(30자)이고, 사용자가 이름 뒤에 덧붙일 여유를 둔다.
        @NotBlank
        @Size(max = 40, message = "방 이름은 40자 이하여야 합니다.")
        String title
) {
}
