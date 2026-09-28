package com.soksak.soksak.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 20)
        String nickname
) {
    // CreateUserRequest와 같은 이유로 앞뒤 공백을 뗀다.
    public UpdateUserRequest {
        if (nickname != null) nickname = nickname.strip();
    }
}
