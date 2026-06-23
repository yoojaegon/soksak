package com.soksak.soksak.aiClient.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummarizeRequest(
        String existingSummary,
        List<ChatAiRequest.Turn> newMessages
) {
}
