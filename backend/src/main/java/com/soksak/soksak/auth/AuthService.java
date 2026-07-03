package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.LoginRequest;
import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import com.soksak.soksak.user.CustomUserDetails;
import com.soksak.soksak.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.loginId(), request.password())
        );

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        refreshTokenRepository.findByUserId(user.getId())
                        .ifPresentOrElse(
                                rt -> rt.rotate(refreshToken, expiresAt),
                                () -> refreshTokenRepository.save(RefreshToken.builder()
                                        .user(user).refreshToken(refreshToken).expiresAt(expiresAt).build())
                        );

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
    }
}
