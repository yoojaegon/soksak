package com.soksak.soksak.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateUserRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email
    private String email;

    @NotBlank(message = "아이디를 입력해주세요")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
    @Pattern(regexp = "^[a-z0-9]+$", message = "아이디는 영문과 숫자만 사용할 수 있습니다.")
    private String loginId;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 20)
    private String nickname;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
