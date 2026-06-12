package com.soksak.soksak.message.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;

import java.util.List;

public interface ChatAiClient {
    String reply(ChatRoom room, List<Message> history);
}
