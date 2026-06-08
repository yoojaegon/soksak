package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse reissue(String refreshToken){
        if (!tokenProvider.validToken(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 토큰");
        }
        RefreshToken stored = refreshTokenService.findByRefreshToken(refreshToken);
        Authentication auth = tokenProvider.getAuthentication(refreshToken);

        String newAccess = tokenProvider.generateAccessToken(auth);
        String newRefresh = tokenProvider.generateRefreshToken(auth);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        stored.rotate(newRefresh, expiresAt);
        return new TokenResponse(newAccess, newRefresh);
    }
}
