package com.soksak.soksak.message.dto;

import jakarta.validation.constraints.NotBlank;

// 메시지 수정용. 길이 한도는 유저 메시지에만 있어서 여기서 정하지 않고
// Message.exceedsEditLimit()로 서비스에서 확인한다.
public record UpdateMessageRequest(
        @NotBlank String content
) {
}
