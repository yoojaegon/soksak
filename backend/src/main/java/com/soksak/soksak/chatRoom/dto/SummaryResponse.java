package com.soksak.soksak.chatRoom.dto;

import com.soksak.soksak.chatRoom.ChatRoom;

public record SummaryResponse(
    String summary
) {
    public static SummaryResponse from(ChatRoom room) {
        return new SummaryResponse(room.getSummary());
    }
}
