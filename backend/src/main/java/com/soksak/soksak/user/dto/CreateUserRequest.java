package com.soksak.soksak.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateUserRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email
    private String email;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 20)
    private String nickname;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
