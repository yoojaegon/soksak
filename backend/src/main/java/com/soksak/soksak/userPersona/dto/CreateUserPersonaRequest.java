package com.soksak.soksak.userPersona.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserPersonaRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
        String name,
        @NotBlank(message = "페르소나는 필수입니다.")
        @Size(max = 1000, message = "페르소나는 1000자 이하여야 합니다.")
        String persona,
        // 만들면서 바로 기본 페르소나로 지정할지. 보내지 않으면 지정하지 않는다.
        Boolean isDefault
) {
}
