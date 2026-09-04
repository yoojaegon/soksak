package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface ChatAiClient {
    String reply(ChatRoom room, String content, List<Message> priorHistory);
    // 요약문만이 아니라 importance/keywords/tokenCount까지 그대로 돌려준다 — 셋 다 조각을
    // 저장할 때 필요하고, 여기서 String으로 줄이면 호출부가 다시 만들 방법이 없다.
    SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch);
    String replyStream(ChatRoom room, String content, List<Message> priorHistory, Consumer<String> onToken);
}
