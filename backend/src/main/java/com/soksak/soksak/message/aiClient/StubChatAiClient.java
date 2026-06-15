package com.soksak.soksak.message.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("test")   // 테스트에서는 ai-server 없이 이 스텁으로 동작
public class StubChatAiClient implements ChatAiClient{
    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory) {
        return "(ai 서버 연결 예정) " + room.getCharacter().getName() + " 응답 자리";
    }
}
