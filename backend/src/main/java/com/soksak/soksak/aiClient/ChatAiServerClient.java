package com.soksak.soksak.aiClient;

import com.soksak.soksak.aiClient.dto.SummarizeRequest;
import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.SummarySelector;
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
@Profile("!test")   // test 프로필에서는 StubChatAiClient를 쓴다
@RequiredArgsConstructor
public class ChatAiServerClient implements ChatAiClient{
    private final RestClient aiServerRestClient;
    private final UserPersonaRepository userPersonaRepository;
    private final LoreService loreService;

    @Override
    public String reply(ChatRoom room, String content, List<Message> priorHistory, List<ChatSummary> summaries) {
        ChatAiRequest request = buildRequest(room, content, priorHistory, summaries);

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
    public SummarizeResponse summarize(List<String> previousSummaries, List<Message> batch) {
        SummarizeRequest request = new SummarizeRequest(previousSummaries, toTurns(batch));

        SummarizeResponse response = callAiServer(() -> aiServerRestClient.post()
                .uri("/summarize")
                .body(request)
                .retrieve()
                .body(SummarizeResponse.class));

        if (response == null || response.summary() == null || response.summary().isBlank()) {
            log.warn("AI 서버가 빈 요약을 반환함");
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return response;
    }

    /**
     * ai-server의 /chat/stream(SSE)을 열어 토큰을 onToken으로 흘려보내고, 전체 답변을 모아 돌려준다.
     * 모은 답변은 스트림이 끝난 뒤 assistant 메시지로 저장한다.
     */
    @Override
    public String replyStream(ChatRoom room, String content, List<Message> priorHistory,
                              List<ChatSummary> summaries, Consumer<String> onToken) {
        ChatAiRequest request = buildRequest(room, content, priorHistory, summaries);
        try {
            // 스트리밍이라 retrieve() 대신 exchange()로 응답 바디를 직접 읽는다.
            // 바디(readSse)는 이 람다 안에서 다 읽어야 한다. 람다를 벗어나면 커넥션이 닫힌다.
            return aiServerRestClient.post()
                    .uri("/chat/stream")
                    .body(request)
                    .exchange((req, res) -> {
                        // exchange()는 4xx/5xx에도 예외를 던지지 않아서 상태 코드를 직접 확인한다.
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            log.warn("AI 스트림 응답 상태 이상 (status={}, model={}, roomId={})",
                                    res.getStatusCode(), request.model(), room.getId());
                            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                        }
                        return readSse(res.getBody(), onToken, request.model(), room.getId());
                    });
        } catch (RestClientException e) {   // 연결 실패, 타임아웃 등
            log.warn("AI 스트림 호출 실패 (model={}, roomId={})", request.model(), room.getId(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private ChatAiRequest buildRequest(ChatRoom room, String content,
                                       List<Message> priorHistory, List<ChatSummary> summaries) {
        // 커서는 조각 전체 기준이다(SummarySelector.cursorOf 참고). 아래 select()가 예산 때문에
        // 일부 조각을 빼더라도 그 구간은 이미 요약된 것으로 친다. 그래야 같은 대화가 요약과 원문으로
        // 두 번 실리지 않는다. 기록에 빠진 구간이 있다는 건 프롬프트가 모델에게 알려준다.
        long upTo = SummarySelector.cursorOf(summaries);
        List<Message> unsummarized = priorHistory.stream().filter(m -> m.getId() > upTo).toList();

        // 요약되지 않은 이전 대화를 {role, content} 목록으로 바꾼다. 요약이 밀리면 이 부분이
        // 계속 쌓이므로 개수가 아니라 토큰 예산으로 자른다(HistoryTrimmer).
        List<ChatAiRequest.Turn> recent = toTurns(HistoryTrimmer.trim(unsummarized));

        // 유저의 기본 페르소나. 가입할 때 만들고 마지막 하나는 지울 수 없어서 항상 하나 있다.
        // 없다면 데이터가 깨진 것이라 기본값으로 넘어가지 않고 실패시킨다.
        UserPersona persona = userPersonaRepository
                .findByUser_IdAndIsDefaultTrue(room.getUser().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "기본 페르소나가 없음 (userId=" + room.getUser().getId() + ", roomId=" + room.getId() + ")"));
        String userName = persona.getName();
        String userPersona = persona.getPersona();
        String mode = room.isWritingToggle() ? "writing" : "rp";
        ChatAiRequest.Config config = new ChatAiRequest.Config(mode, room.isFoldSpoilerToggle());

        // 켜진 로어 중 키워드가 유저 입력이나 최근 대화에 걸린 것을 고른다.
        List<String> lore = loreService.selectLore(room.getCharacter().getId(), content, recent.stream().map(ChatAiRequest.Turn::content).toList());

        String model = ModelCatalog.resolve(room.effectiveModel());
        // 모델이 받을 수 있는 값으로 맞춰서 보낸다. 방에 저장된 값은 그대로 둔다.
        // 추론 없는 모델로 잠깐 바꿨다가 돌아와도 원래 고른 레벨이 남아 있어야 한다.
        ThinkingLevel thinking = ModelCatalog.resolveThinking(model, room.effectiveThinkingLevel());

        // 조각 전부가 아니라 예산 안에서 고른 것만 싣는다.
        // 고른 게 없으면 null이고, ai-server는 기억 블록을 통째로 뺀다.
        String memory = SummarySelector.join(SummarySelector.select(summaries));

        return new ChatAiRequest(
                room.getCharacter().getPersona(),
                content,
                recent,
                lore,
                memory,
                room.getCharacter().getName(),
                userName,
                userPersona,
                model,
                thinking,
                config
        );
    }

    /**
     * ai-server의 SSE 스트림을 줄 단위로 읽어 토큰을 되살린다.
     * ai-server는 토큰 하나를 여러 data 줄과 빈 줄(이벤트 경계)로 보내므로,
     * 빈 줄이 나올 때까지 data 줄을 모아야 토큰 하나가 된다.
     */
    private String readSse(InputStream body, Consumer<String> onToken, String model, Long roomId) throws IOException {
        StringBuilder full = new StringBuilder();   // 저장할 전체 답변
        StringBuilder data = new StringBuilder();   // 지금 이벤트의 data 줄을 모은다
        boolean sawData = false;                     // 이번 이벤트에서 data 줄을 하나라도 봤는지
        boolean isError = false;                     // 직전에 'event: error'를 봤는지

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {                       // 빈 줄은 이벤트 경계다. 토큰 하나가 완성된다
                    if (isError) {
                        log.warn("AI 스트림 에러 이벤트 수신 (model={}, roomId={})", model, roomId);
                        throw new BusinessException(ErrorCode.AI_UNAVAILABLE); // ai-server 내부 에러
                    }
                    String token = data.toString();
                    data.setLength(0);
                    sawData = false;
                    if ("[DONE]".equals(token)) break;      // 정상 종료 신호
                    if (!token.isEmpty()) {
                        full.append(token);
                        onToken.accept(token);              // 프론트로 흘려보낸다
                    }
                } else if (line.startsWith("data:")) {
                    String value = line.substring(5);       // 'data:' 접두를 뗀다
                    // SSE 규격대로 콜론 뒤 공백 하나만 뗀다. LLM 토큰은 ' 오늘'처럼 공백으로
                    // 시작하는 경우가 많아서, trim()을 쓰면 단어가 붙어 버린다.
                    if (value.startsWith(" ")) value = value.substring(1);
                    // data 줄이 여러 개면 개행으로 잇는다. 내용이 비었는지가 아니라 앞서 data 줄을
                    // 봤는지로 판단해야 빈 data 줄(개행 하나)도 제대로 되살아난다.
                    if (sawData) data.append("\n");
                    data.append(value);
                    sawData = true;
                } else if (line.startsWith("event:")) {
                    isError = line.substring(6).trim().equals("error");
                }
                // ':'로 시작하는 주석 줄 같은 나머지 필드는 무시한다
            }
        }

        // [DONE]도 내용도 없이 스트림이 끝나면(연결이 끊긴 경우 등) 실패로 본다.
        if (full.isEmpty()) {
            log.warn("AI 스트림이 빈 응답으로 종료됨 (model={}, roomId={})", model, roomId);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        return full.toString();
    }

    /**
     * ai-server 호출 공통 처리. 서버 다운, 타임아웃, 4xx/5xx(RestClientException)는
     * 원인을 로그로 남기고 503으로 바꾼다.
     */
    private <T> T callAiServer(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.warn("AI 서버 호출 실패", e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /** Message 목록을 ai-server가 받는 {role, content} 턴 목록으로 바꾼다. */
    private List<ChatAiRequest.Turn> toTurns(List<Message> messages) {
        return messages.stream()
                .map(m -> new ChatAiRequest.Turn(m.getRole().name().toLowerCase(Locale.ROOT), m.getContent()))
                .toList();
    }
}
