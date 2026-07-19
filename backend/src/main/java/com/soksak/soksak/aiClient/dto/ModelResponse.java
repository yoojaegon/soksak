package com.soksak.soksak.aiClient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.soksak.soksak.aiClient.ModelCatalog;

import java.util.List;

public record ModelResponse(
        List<ModelCatalog.Entry> models,
        @JsonProperty("default") String defaultModel
) {
}
