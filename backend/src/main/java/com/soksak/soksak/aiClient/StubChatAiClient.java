package com.soksak.soksak.aiClient;

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

    @Override
    public String summarize(String existingSummary, List<Message> batch) {
        return "(요약 stub)";
    }

    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory, Consumer<String> onToken) {
        String reply = reply(room, content, priorHistory);  // 기존 스텁 문자열 재사용
        onToken.accept(reply);                               // 한 조각으로 흘려보냄
        return reply;
    }
}
