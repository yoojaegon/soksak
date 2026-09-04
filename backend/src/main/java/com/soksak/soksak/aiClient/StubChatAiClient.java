package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
@Profile("test")   // 테스트에서는 ai-server 없이 이 스텁으로 동작
public class StubChatAiClient implements ChatAiClient{
    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory) {
        return "(ai 서버 연결 예정) " + room.getCharacter().getName() + " 응답 자리";
    }

    // importance는 null로 둬서 엔티티 기본값(3)을 타게 하고, 토큰 수는 스텁 문자열 길이에
    // 맞춘 작은 값을 준다 — 0을 주면 조각이 예산을 안 먹어서 선택 로직이 테스트에서 안 걸린다.
    @Override
    public SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch) {
        return new SummarizeResponse("(요약 stub)", null, List.of(), 20);
    }

    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory, Consumer<String> onToken) {
        String reply = reply(room, content, priorHistory);  // 기존 스텁 문자열 재사용
        onToken.accept(reply);                               // 한 조각으로 흘려보냄
        return reply;
    }
}
