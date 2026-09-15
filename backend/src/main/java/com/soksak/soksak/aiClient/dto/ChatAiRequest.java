package com.soksak.soksak.aiClient.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.soksak.soksak.aiClient.ThinkingLevel;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatAiRequest (
        String persona,
        String userMessage,
        List<Turn> recentMessages,
        List<String> loreEntries,
        String summary,
        String charName,
        String userName,
        String userPersona,
        String model,
        // 모델 파라미터라서 config가 아니라 model 옆에 둔다 — Config는 프롬프트 조립 설정이다.
        // 이미 모델에 맞게 보정된 값이 온다(ModelCatalog.resolveThinking).
        ThinkingLevel thinking,
        Config config
) {
    public record Turn(String role, String content) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Config(String mode, boolean foldSpoilers) {}
}
