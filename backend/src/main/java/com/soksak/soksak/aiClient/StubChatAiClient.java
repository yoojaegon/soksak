package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.message.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
@Profile("test")   // 테스트에서는 ai-server 없이 이 스텁으로 동작
public class StubChatAiClient implements ChatAiClient{
    // 스텁은 프롬프트를 조립하지 않으므로 summaries를 쓰지 않는다. 조각이 실제로 프롬프트에
    // 실리는지는 여기서 검증되지 않는다 — SummarySelector 단위테스트가 그 몫을 맡는다.
    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory, List<ChatSummary> summaries) {
        return "(ai 서버 연결 예정) " + room.getCharacter().getName() + " 응답 자리";
    }

    // importance는 null로 둬서 엔티티 기본값(3)을 타게 하고, 토큰 수는 스텁 문자열 길이에
    // 맞춘 작은 값을 준다 — 0을 주면 조각이 예산을 안 먹어서 선택 로직이 테스트에서 안 걸린다.
    @Override
    public SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch) {
        return new SummarizeResponse("(요약 stub)", null, List.of(), 20);
    }

    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory,
                              List<ChatSummary> summaries, Consumer<String> onToken) {
        String reply = reply(room, content, priorHistory, summaries);  // 기존 스텁 문자열 재사용
        onToken.accept(reply);                               // 한 조각으로 흘려보냄
        return reply;
    }
}
