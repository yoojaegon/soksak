package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeRequest;
import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.aiClient.dto.ChatAiRequest;
import com.soksak.soksak.aiClient.dto.ChatAiResponse;
import com.soksak.soksak.userPersona.UserPersona;
import com.soksak.soksak.userPersona.UserPersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;

@Component
@Profile("!test")   // test 프로필에서는 StubChatAiClient가 대신 쓰임
@RequiredArgsConstructor
public class ChatAiServerClient implements ChatAiClient{
    private final RestClient aiServerRestClient;
    private final UserPersonaRepository userPersonaRepository;

    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory) {
        Long upTo = room.getSummarizedUpToId();
        List<Message> unsummarized = (upTo == null)
                ? priorHistory
                : priorHistory.stream().filter(m -> m.getId() > upTo).toList();

        // 이전 대화 -> {role, content} 리스트로 변환
        List<ChatAiRequest.Turn> recent = toTurns(unsummarized);

        // 유저의 기본 페르소나 -> user_name / user_persona (없으면 null, ai-server가 기본값 처리)
        UserPersona persona = userPersonaRepository
                .findByUserAndIsDefaultTrue(room.getUser())
                .orElse(null);
        String userName = persona != null ? persona.getName() : null;
        String userPersona = persona != null ? persona.getPersona() : null;
        String mode = room.isWritingToggle() ? "writing" : "rp";
        ChatAiRequest.Config config = new ChatAiRequest.Config(mode, room.isFoldSpoilerToggle());

        ChatAiRequest request = new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                List.of(),
                room.getSummary(),
                room.getCharacter().getName(),
                userName,
                userPersona,
                config
        );

        ChatAiResponse response = aiServerRestClient.post()
                .uri("/chat")
                .body(request)
                .retrieve()
                .body(ChatAiResponse.class);

        return response.answer();
    }

    @Override
    public String summarize(String existingSummary, List<Message> batch) {
        SummarizeRequest request = new SummarizeRequest(existingSummary, toTurns(batch));

        SummarizeResponse response = aiServerRestClient.post()
                .uri("/summarize")
                .body(request)
                .retrieve()
                .body(SummarizeResponse.class);

        return response.summary();
    }

    // Message 목록 -> ai-server가 받는 {role, content} 턴 리스트로 변환
    private List<ChatAiRequest.Turn> toTurns(List<Message> messages) {
        return messages.stream()
                .map(m -> new ChatAiRequest.Turn(m.getRole().name().toLowerCase(Locale.ROOT), m.getContent()))
                .toList();
    }
}
