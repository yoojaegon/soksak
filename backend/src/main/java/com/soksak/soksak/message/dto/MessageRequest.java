package com.soksak.soksak.message.dto;

import com.soksak.soksak.message.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 새 메시지를 보낼 때 쓴다. 수정은 AI 답변에는 한도가 없어서 UpdateMessageRequest를 따로 둔다.
public record MessageRequest (
        @NotBlank
        @Size(max = Message.USER_CONTENT_MAX, message = "메시지는 " + Message.USER_CONTENT_MAX + "자 이하여야 합니다.")
        String content
){
}
