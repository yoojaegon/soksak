package com.soksak.soksak.config.jwt;

import java.time.LocalDateTime;

// 발급한 토큰과 만료 시각. 리프레시 토큰은 DB에 만료 시각도 같이 저장하므로 둘을 함께 돌려준다.
public record IssuedToken(String value, LocalDateTime expiresAt) {
}
