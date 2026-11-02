package com.soksak.soksak.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$",
                message = "비밀번호는 영문·숫자·특수문자만 쓸 수 있고, 영문과 숫자를 각각 하나 이상 포함해야 합니다."
        )
        String newPassword
) {
}
