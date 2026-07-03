package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;


    @Transactional
    public TokenResponse reissue(String refreshToken){
        // 서명 검증+파싱을 한 번만 하고, 그 Claims로 타입 확인·인증 복원까지 재사용한다.
        Claims claims = jwtTokenProvider.parse(refreshToken)
                .filter(c -> JwtTokenProvider.TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(c)))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        Authentication auth = jwtTokenProvider.getAuthentication(claims);

        String newAccess = jwtTokenProvider.generateAccessToken(auth);
        String newRefresh = jwtTokenProvider.generateRefreshToken(auth);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        if (refreshTokenRepository.rotate(refreshToken, newRefresh, expiresAt) != 1) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return new TokenResponse(newAccess, newRefresh);
    }
}
