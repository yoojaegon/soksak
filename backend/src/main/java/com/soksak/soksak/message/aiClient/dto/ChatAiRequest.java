package com.soksak.soksak.message.aiClient.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatAiRequest (
        String persona,
        String userMessage,
        List<Turn> recentMessages,
        List<String> loreEntries,
        String summary
) {
    public record Turn(String role, String content) {}
}
