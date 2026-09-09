package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface ChatAiClient {
    // summaries는 방의 요약 조각 전체(seq 오름차순). 프롬프트에 실을 것을 고르는 일도, 어디까지
    // 요약됐는지 커서를 잡는 일도 구현체가 여기서 한다 — 호출부는 조각을 그대로 넘기기만 한다.
    String reply(ChatRoom room, String content, List<Message> priorHistory, List<ChatSummary> summaries);
    // 요약문만이 아니라 importance/keywords/tokenCount까지 그대로 돌려준다 — 셋 다 조각을
    // 저장할 때 필요하고, 여기서 String으로 줄이면 호출부가 다시 만들 방법이 없다.
    SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch);
    String replyStream(ChatRoom room, String content, List<Message> priorHistory,
                       List<ChatSummary> summaries, Consumer<String> onToken);
}
