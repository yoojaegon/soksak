package com.soksak.soksak.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;

import java.util.List;

public interface ChatAiClient {
    String reply(ChatRoom room, String content, List<Message> priorHistory);
}
