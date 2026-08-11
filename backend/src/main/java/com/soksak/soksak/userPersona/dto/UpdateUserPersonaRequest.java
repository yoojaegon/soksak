package com.soksak.soksak.userPersona.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserPersonaRequest(
        @NotBlank String name,
        @NotBlank String persona
) {
}
