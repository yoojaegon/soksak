package com.soksak.soksak.message.dto;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;

import java.util.List;

public record StreamJob(
        ChatRoom room,
        String content,
        List<Message> priorHistory
) {
}
