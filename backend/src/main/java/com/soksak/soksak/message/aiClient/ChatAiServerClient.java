package com.soksak.soksak.message.aiClient;

import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.aiClient.dto.ChatAiRequest;
import com.soksak.soksak.message.aiClient.dto.ChatAiResponse;
import com.soksak.soksak.userPersona.UserPersona;
import com.soksak.soksak.userPersona.UserPersonaRepository;
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
    private final UserPersonaRepository userPersonaRepository;

    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory) {
        // 이전 대화 -> {role, content} 리스트로 변환
        List<ChatAiRequest.Turn> recent = priorHistory.stream()
                .map(m -> new ChatAiRequest.Turn(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();

        // 유저의 기본 페르소나 -> user_name / user_persona (없으면 null, ai-server가 기본값 처리)
        UserPersona persona = userPersonaRepository
                .findByUserAndIsDefaultTrue(room.getUser())
                .orElse(null);
        String userName = persona != null ? persona.getName() : null;
        String userPersona = persona != null ? persona.getPersona() : null;

        ChatAiRequest request = new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                List.of(),
                null,
                room.getCharacter().getName(),
                userName,
                userPersona
        );

        ChatAiResponse response = aiServerRestClient.post()
                .uri("/chat")
                .body(request)
                .retrieve()
                .body(ChatAiResponse.class);

        return response.answer();
    }
}
