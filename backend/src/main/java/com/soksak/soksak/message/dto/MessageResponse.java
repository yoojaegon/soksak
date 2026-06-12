package com.soksak.soksak.message.dto;

import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRole;

import java.time.LocalDateTime;

public record MessageResponse (
        Long id,
        MessageRole role,
        String content,
        LocalDateTime createdAt
){
    public static MessageResponse from(Message message){
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
