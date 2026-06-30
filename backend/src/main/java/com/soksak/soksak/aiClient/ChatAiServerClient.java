package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeRequest;
import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.lore.LoreRepository;
import com.soksak.soksak.lore.LoreService;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.aiClient.dto.ChatAiRequest;
import com.soksak.soksak.aiClient.dto.ChatAiResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.userPersona.UserPersona;
import com.soksak.soksak.userPersona.UserPersonaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Component
@Profile("!test")   // test 프로필에서는 StubChatAiClient가 대신 쓰임
@RequiredArgsConstructor
public class ChatAiServerClient implements ChatAiClient{
    private final RestClient aiServerRestClient;
    private final UserPersonaRepository userPersonaRepository;
    private final LoreService loreService;

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


        // 로어북 enabled=true 조회 + 키워드가 최근 메시지와 유저 입력에 매칭되는지 확인
        List<String> lore = loreService.selectLore(room.getCharacter().getId(), content, recent.stream().map(ChatAiRequest.Turn::content).toList());

        ChatAiRequest request = new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                lore,
                room.getSummary(),
                room.getCharacter().getName(),
                userName,
                userPersona,
                config
        );

        ChatAiResponse response = callAiServer(() -> aiServerRestClient.post()
                .uri("/chat")
                .body(request)
                .retrieve()
                .body(ChatAiResponse.class));

        if (response == null || response.answer() == null || response.answer().isBlank()) {
            log.warn("AI 서버가 빈 응답을 반환함 (roomId={})", room.getId());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return response.answer();
    }

    @Override
    public String summarize(String existingSummary, List<Message> batch) {
        SummarizeRequest request = new SummarizeRequest(existingSummary, toTurns(batch));

        SummarizeResponse response = callAiServer(() -> aiServerRestClient.post()
                .uri("/summarize")
                .body(request)
                .retrieve()
                .body(SummarizeResponse.class));

        if (response == null || response.summary() == null || response.summary().isBlank()) {
            log.warn("AI 서버가 빈 요약을 반환함");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return response.summary();
    }

    // AI 서버 호출 공통 래퍼: 다운/타임아웃/4xx/5xx(RestClientException)를 503으로 변환하고 원인은 로깅.
    private <T> T callAiServer(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.warn("AI 서버 호출 실패", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    // Message 목록 -> ai-server가 받는 {role, content} 턴 리스트로 변환
    private List<ChatAiRequest.Turn> toTurns(List<Message> messages) {
        return messages.stream()
                .map(m -> new ChatAiRequest.Turn(m.getRole().name().toLowerCase(Locale.ROOT), m.getContent()))
                .toList();
    }
}
