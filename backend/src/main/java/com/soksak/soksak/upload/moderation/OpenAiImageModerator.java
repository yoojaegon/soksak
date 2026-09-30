package com.soksak.soksak.upload.moderation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

// OpenAI omni-moderation으로 이미지를 검사한다. 엔드포인트는 무료지만 계정 등급별 레이트 리밋이 있다.
// RestClient를 빈으로 두지 않고 여기서 만든다 — RestClient 빈이 둘이 되면 ChatAiServerClient 주입이
// 파라미터 이름에 기대게 되고, 이 클라이언트는 여기 말고 쓰는 곳이 없다.
@Slf4j
@Component
@Profile("!test")   // test 프로필에서는 StubImageModerator가 대신 쓰임
public class OpenAiImageModerator implements ImageModerator {
    private final RestClient restClient;
    private final String model;
    private final boolean configured;

    public OpenAiImageModerator(
            @Value("${moderation.api-key:}") String apiKey,
            @Value("${moderation.base-url}") String baseUrl,
            @Value("${moderation.model}") String model,
            @Value("${moderation.connect-timeout}") int connectTimeout,
            @Value("${moderation.read-timeout}") int readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(factory)
                .build();
        this.model = model;
        // 키가 없어도 앱은 띄운다(업로드 말고는 영향이 없으므로). 대신 업로드는 전부 거부된다.
        this.configured = !apiKey.isBlank();
        if (!configured) {
            log.warn("OPENAI_API_KEY가 비어 있어 이미지 업로드가 전부 거부됩니다.");
        }
    }

    @Override
    public ModerationScores score(byte[] image, String mimeType) {
        if (!configured) {
            throw new BusinessException(ErrorCode.IMAGE_MODERATION_UNAVAILABLE);
        }

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
        ModerationRequest request = new ModerationRequest(model, List.of(ModerationInput.image(dataUrl)));

        ModerationResponse response;
        try {
            response = restClient.post()
                    .uri("/moderations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ModerationResponse.class);
        } catch (RestClientException e) {
            log.error("이미지 검사 호출 실패", e);
            throw new BusinessException(ErrorCode.IMAGE_MODERATION_UNAVAILABLE);
        }

        Map<String, Double> raw = response == null || response.results() == null || response.results().isEmpty()
                ? null
                : response.results().get(0).categoryScores();
        ModerationScores scores = ModerationScores.from(raw);
        if (scores == null) {
            log.error("이미지 검사 응답에 필요한 점수가 없음 scores={}", raw);
            throw new BusinessException(ErrorCode.IMAGE_MODERATION_UNAVAILABLE);
        }
        return scores;
    }

    record ModerationRequest(String model, List<ModerationInput> input) {}

    record ModerationInput(String type, @JsonProperty("image_url") ImageUrl imageUrl) {
        static ModerationInput image(String url) {
            return new ModerationInput("image_url", new ImageUrl(url));
        }
    }

    record ImageUrl(String url) {}

    record ModerationResponse(List<Result> results) {}

    // 키에 슬래시가 들어가서(violence/graphic) 필드가 아니라 Map으로 받는다.
    record Result(@JsonProperty("category_scores") Map<String, Double> categoryScores) {}
}
