package com.soksak.soksak.aiClient.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** ai-server /summarize 요청 본문. 필드는 ai-server app/api/chat.py의 SummarizeRequest와 맞춘다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummarizeRequest(
        List<String> previousSummaries,
        List<ChatAiRequest.Turn> newMessages
) {
}
