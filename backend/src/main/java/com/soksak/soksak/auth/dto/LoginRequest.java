package com.soksak.soksak.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password
){
    // 아이디는 가입할 때 소문자만 받으므로 로그인 입력도 공백을 떼고 소문자로 바꿔 찾는다.
    // 모바일 자동 대문자로 "Tester"가 들어와도 같은 계정으로 로그인되게 하기 위해서다.
    // 비밀번호는 입력 그대로 비교해야 하므로 건드리지 않는다.
    public LoginRequest {
        if (loginId != null) loginId = loginId.strip().toLowerCase(Locale.ROOT);
    }
}
