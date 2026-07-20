package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeRequest;
import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
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
        ChatAiRequest request = buildRequest(room, content, priorHistory);

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

    // ai-server의 /chat/stream(SSE)을 열어 토큰을 onToken으로 흘려보내고, 전체 답변을 누적해 리턴한다.
    // (누적본은 스트림이 끝난 뒤 assistant 메시지로 저장하는 용도)
    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory, Consumer<String> onToken) {
        ChatAiRequest request = buildRequest(room, content, priorHistory);
        try {
            // 스트리밍이라 retrieve()가 아닌 exchange()로 응답 바디를 직접 읽는다.
            // 바디 소비(readSse)는 반드시 이 람다 안에서 끝내야 한다 — 밖으로 나가면 커넥션이 닫힌다.
            return aiServerRestClient.post()
                    .uri("/chat/stream")
                    .body(request)
                    .exchange((req, res) -> {
                        // exchange()는 4xx/5xx에 예외를 던지지 않으므로 상태코드를 직접 확인한다.
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                        }
                        return readSse(res.getBody(), onToken);
                    });
        } catch (RestClientException e) {   // 연결 실패/타임아웃 등
            log.warn("AI 스트림 호출 실패", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private ChatAiRequest buildRequest(ChatRoom room, String content, List<Message> priorHistory) {
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

        String model = ModelCatalog.resolve(room.getModel());

        return new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                lore,
                room.getSummary(),
                room.getCharacter().getName(),
                userName,
                userPersona,
                model,
                config
        );
    }

    // ai-server의 SSE 스트림을 줄 단위로 읽어 토큰을 복원한다.
    // ai-server는 토큰 하나를 'data: ...' 여러 줄 + 빈 줄(이벤트 경계)로 보내므로,
    // 빈 줄이 나올 때까지 data 줄을 모아야 토큰 하나가 완성된다.
    private String readSse(InputStream body, Consumer<String> onToken) throws IOException {
        StringBuilder full = new StringBuilder();   // 저장용 전체 답변 누적
        StringBuilder data = new StringBuilder();   // 현재 이벤트의 data 줄 조립
        boolean sawData = false;                     // 이번 이벤트에서 data 줄을 하나라도 봤는지
        boolean isError = false;                     // 직전에 'event: error'를 봤는지

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {                       // ── 빈 줄 = 이벤트 경계, 토큰 하나 완성
                    if (isError) throw new BusinessException(ErrorCode.AI_UNAVAILABLE); // ai-server 내부 에러
                    String token = data.toString();
                    data.setLength(0);
                    sawData = false;
                    if ("[DONE]".equals(token)) break;      // 정상 종료 신호
                    if (!token.isEmpty()) {
                        full.append(token);
                        onToken.accept(token);              // 프론트로 흘려보냄
                    }
                } else if (line.startsWith("data:")) {
                    String value = line.substring(5);       // 'data:' 접두 제거
                    // SSE 규격상 콜론 뒤 공백 1개만 벗긴다. LLM 토큰은 ' 오늘'처럼 앞 공백으로
                    // 시작하는 게 흔한데, trim()으로 다 날리면 단어가 붙어버린다.
                    if (value.startsWith(" ")) value = value.substring(1);
                    // 여러 data 줄은 개행으로 이어 붙인다(개행 포함 토큰 복원). '내용이 비었는지'가 아니라
                    // '앞서 data 줄을 봤는지'로 판단해야 빈 data 줄(=개행)도 제대로 복원된다.
                    if (sawData) data.append("\n");
                    data.append(value);
                    sawData = true;
                } else if (line.startsWith("event:")) {
                    isError = line.substring(6).trim().equals("error");
                }
                // ':' 주석 줄 등 그 밖의 필드는 무시
            }
        }

        // [DONE]도 못 보고 내용도 없이 스트림이 끝난 경우(연결 중단 등)는 실패로 처리한다.
        if (full.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return full.toString();
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
