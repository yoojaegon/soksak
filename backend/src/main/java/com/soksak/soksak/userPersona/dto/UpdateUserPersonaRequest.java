package com.soksak.soksak.userPersona.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserPersonaRequest(
        @NotBlank(message = "이름은 필수입니다.") String name,
        @NotBlank(message = "페르소나는 필수입니다.") String persona
) {
}
