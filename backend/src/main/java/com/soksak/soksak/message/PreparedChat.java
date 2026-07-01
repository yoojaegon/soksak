package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;

import java.util.List;

public record PreparedChat(
        ChatRoom room,
        List<Message> priorHistory
) {
}
