package com.soksak.soksak.auth;

// 인증된 사용자. 컨트롤러는 @AuthenticationPrincipal로 이걸 받는다.
// 토큰이 살아 있는 동안 값이 그대로 남으니, 닉네임처럼 바뀌는 값은 넣지 않는다.
public record AuthUser(
        Long id
) {
}
