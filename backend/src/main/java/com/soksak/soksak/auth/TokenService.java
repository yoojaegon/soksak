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
        // 서명 검증+파싱을 한 번만 하고, 그 Claims로 타입 확인·인증 복원까지 재사용한다.
        Claims claims = jwtTokenProvider.parse(refreshToken)
                .filter(c -> JwtTokenProvider.TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(c)))
                .orElseThrow(() -> {
                    // 서명이 깨졌거나 만료됐거나 액세스 토큰을 보낸 경우. 만료는 흔하지만
                    // 서명 위조도 같은 자리로 떨어지므로 warn으로 둔다.
                    log.warn("재발급 거부 — 리프레시 토큰이 유효하지 않음");
                    return new BusinessException(ErrorCode.INVALID_TOKEN);
                });
        Authentication auth = jwtTokenProvider.getAuthentication(claims);

        String newAccess = jwtTokenProvider.generateAccessToken(auth);
        String newRefresh = jwtTokenProvider.generateRefreshToken(auth);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        if (refreshTokenRepository.rotate(refreshToken, newRefresh, expiresAt) != 1) {
            // 서명은 멀쩡한데 DB에 그 토큰이 없다 = 이미 한 번 회전됐다는 뜻.
            // 정상 사용자는 옛 토큰을 다시 쓸 일이 없으므로 **탈취된 토큰의 재사용 신호**로 본다.
            // (로그아웃 후 재시도 같은 양성도 섞이니 경보가 아니라 단서로 쓸 것.)
            log.warn("리프레시 토큰 재사용 탐지 loginId={}", claims.getSubject());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        log.info("토큰 재발급 loginId={}", claims.getSubject());
        return new TokenResponse(newAccess, newRefresh);
    }
}
