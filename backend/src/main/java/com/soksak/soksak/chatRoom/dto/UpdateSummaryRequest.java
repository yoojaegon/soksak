package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSummaryRequest(
        @Size(max = 3000) @NotNull String summary
) {
}
