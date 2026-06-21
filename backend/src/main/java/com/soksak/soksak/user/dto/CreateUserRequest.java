package com.soksak.soksak.user.dto;

import com.soksak.soksak.common.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email
        String email,

        @NotBlank(message = "아이디를 입력해주세요")
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
        @Pattern(regexp = "^[a-z0-9]+$", message = "아이디는 영문과 숫자만 사용할 수 있습니다.")
        String loginId,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 20)
        String nickname,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotNull(message = "나이는 필수입니다.")
        @Min(value = 0, message = "나이는 0 이상이어야 합니다.")
        Integer age,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {
}