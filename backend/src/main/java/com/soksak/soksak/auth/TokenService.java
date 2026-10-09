package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;


    @Transactional
    public TokenResponse reissue(String refreshToken){
        // 서명 검증과 파싱은 한 번만 하고, 꺼낸 Claims로 토큰 종류 확인과 인증 정보 복원까지 한다.
        Claims claims = jwtTokenProvider.parse(refreshToken)
                .filter(c -> JwtTokenProvider.TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(c)))
                .orElseThrow(() -> {
                    // 서명이 깨졌거나 만료됐거나 액세스 토큰을 보낸 경우다.
                    // 대부분은 만료지만 위조된 토큰도 여기로 오기 때문에 warn으로 남긴다.
                    log.warn("재발급 거부 — 리프레시 토큰이 유효하지 않음");
                    return new BusinessException(ErrorCode.INVALID_TOKEN);
                });
        Authentication auth = jwtTokenProvider.getAuthentication(claims);

        String newAccess = jwtTokenProvider.generateAccessToken(auth);
        String newRefresh = jwtTokenProvider.generateRefreshToken(auth);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        if (refreshTokenRepository.rotate(refreshToken, newRefresh, expiresAt) != 1) {
            // 서명은 정상인데 DB에 이 토큰이 없으면 이미 재발급에 한 번 쓰인 토큰이다.
            // 정상 사용자는 옛 토큰을 다시 쓸 일이 없으니, 탈취된 토큰이 재사용됐을 수 있다.
            // 다만 로그아웃 뒤 재시도나 다른 기기 로그인처럼 정상적인 경우도 섞이니 경보가 아니라 단서로 본다.
            log.warn("리프레시 토큰 재사용 탐지 loginId={}", claims.getSubject());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        log.info("토큰 재발급 loginId={}", claims.getSubject());
        return new TokenResponse(newAccess, newRefresh);
    }
}
