package com.soksak.soksak.aiClient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;

import java.util.List;

public record ModelResponse(
        List<ModelCatalog.Entry> models,
        @JsonProperty("default") String defaultModel,
        // 추론 레벨의 전체 어휘. 픽커는 이걸 다 그리고, 모델별 selectable에 없는 값만
        // 비활성으로 표시한다 — 못 고르는 걸 숨기면 왜 없는지 알 수 없다.
        List<ThinkingLevel> thinkingLevels
) {
}
