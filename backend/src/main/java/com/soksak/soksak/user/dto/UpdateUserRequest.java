package com.soksak.soksak.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname
) {
    // 닉네임은 앞뒤 공백을 떼고 저장한다. " foo"와 "foo"를 같은 닉네임으로 보기 위해서다.
    public UpdateUserRequest {
        if (nickname != null) nickname = nickname.strip();
    }
}
