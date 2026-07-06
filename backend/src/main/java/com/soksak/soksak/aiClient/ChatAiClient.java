package com.soksak.soksak.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface ChatAiClient {
    String reply(ChatRoom room, String content, List<Message> priorHistory);
    String summarize(String existingSummary, List<Message> batch);
    String replyStream(ChatRoom room, String content, List<Message> priorHistory, Consumer<String> onToken);
}
