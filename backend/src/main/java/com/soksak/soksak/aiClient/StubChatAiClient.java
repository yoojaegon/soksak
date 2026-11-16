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
@Profile("test")   // 테스트에서는 ai-server 대신 이 스텁을 쓴다
public class StubChatAiClient implements ChatAiClient{
    /**
     * 스텁은 프롬프트를 만들지 않아서 summaries를 쓰지 않는다.
     * 조각이 프롬프트에 제대로 실리는지는 SummarySelector 단위 테스트가 확인한다.
     */
    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory, List<ChatSummary> summaries) {
        return "(ai 서버 연결 예정) " + room.getCharacter().getName() + " 응답 자리";
    }

    /**
     * importance는 null로 둬서 엔티티 기본값(3)을 쓰게 한다.
     * 토큰 수를 0으로 주면 조각이 예산을 차지하지 않아 선택 로직이 테스트에서 돌지 않으므로 작은 값을 준다.
     */
    @Override
    public SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch) {
        return new SummarizeResponse("(요약 stub)", null, List.of(), 20);
    }

    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory,
                              List<ChatSummary> summaries, Consumer<String> onToken) {
        // reply()와 같은 문자열을 한 번에 흘려보낸다
        String reply = reply(room, content, priorHistory, summaries);
        onToken.accept(reply);
        return reply;
    }
}
