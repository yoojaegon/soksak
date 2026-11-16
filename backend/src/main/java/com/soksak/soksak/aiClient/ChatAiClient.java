package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.message.Message;

import java.util.List;
import java.util.function.Consumer;

public interface ChatAiClient {
    /**
     * summaries는 방의 요약 조각 전체다(seq 오름차순). 프롬프트에 실을 조각을 고르는 일과
     * 어디까지 요약됐는지 커서를 잡는 일은 구현체가 한다. 호출하는 쪽은 조각을 그대로 넘기면 된다.
     */
    String reply(ChatRoom room, String content, List<Message> priorHistory, List<ChatSummary> summaries);

    /**
     * 요약문과 함께 importance, keywords, tokenCount를 그대로 돌려준다.
     * 셋 다 조각을 저장할 때 필요하다.
     */
    SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch);

    /** reply()의 스트리밍 버전. 토큰을 onToken으로 흘려보내고, 끝나면 전체 답변을 돌려준다. */
    String replyStream(ChatRoom room, String content, List<Message> priorHistory,
                       List<ChatSummary> summaries, Consumer<String> onToken);
}
