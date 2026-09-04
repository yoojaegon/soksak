package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;

import java.util.List;

public record PreparedChat(
        ChatRoom room,
        List<Message> priorHistory,
        List<ChatSummary> summaries
) {
}
