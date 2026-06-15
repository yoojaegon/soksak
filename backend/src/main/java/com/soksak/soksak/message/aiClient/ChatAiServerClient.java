package com.soksak.soksak.message.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.aiClient.dto.ChatAiRequest;
import com.soksak.soksak.message.aiClient.dto.ChatAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Profile("!test")   // test 프로필에서는 StubChatAiClient가 대신 쓰임
@RequiredArgsConstructor
public class ChatAiServerClient implements ChatAiClient{
    private final RestClient aiServerRestClient;

    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory) {
        // 이전 대화 -> {role, content} 리스트로 변환
        List<ChatAiRequest.Turn> recent = priorHistory.stream()
                .map(m -> new ChatAiRequest.Turn(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();

        ChatAiRequest request = new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                List.of(),
                null
        );

        ChatAiResponse response = aiServerRestClient.post()
                .uri("/chat")
                .body(request)
                .retrieve()
                .body(ChatAiResponse.class);

        return response.answer();
    }
}
