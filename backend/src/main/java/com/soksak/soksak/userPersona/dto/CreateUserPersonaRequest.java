package com.soksak.soksak.userPersona.dto;

import com.soksak.soksak.common.Gender;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserPersonaRequest(
        @NotBlank String name,
        @NotNull Gender gender,
        @NotNull @Min(0) Integer age,
        @NotBlank String persona
) {
}
