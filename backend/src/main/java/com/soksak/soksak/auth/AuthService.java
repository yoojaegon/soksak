package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.LoginRequest;
import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import com.soksak.soksak.user.CustomUserDetails;
import com.soksak.soksak.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.loginId(), request.password())
            );
        } catch (AuthenticationException e) {
            // 실패는 warn으로 남긴다. 한 아이디에 실패가 연달아 쌓이면 공격을 의심할 수 있다.
            log.warn("로그인 실패 loginId={}", forLog(request.loginId()));
            throw e;
        }

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

        // 리프레시 토큰은 사용자당 하나만 둔다. 이미 있으면 값만 바꾸고, 처음이면 새로 만든다.
        // 그래서 다른 기기에서 로그인하면 이전 기기의 리프레시 토큰은 더 이상 쓸 수 없다.
        refreshTokenRepository.findByUserId(user.getId())
                        .ifPresentOrElse(
                                rt -> rt.rotate(refreshToken, expiresAt),
                                () -> refreshTokenRepository.save(RefreshToken.builder()
                                        .user(user).refreshToken(refreshToken).expiresAt(expiresAt).build())
                        );

        log.info("로그인 성공 loginId={} userId={}", user.getLoginId(), user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }

    // loginId를 로그 한 줄에 안전하게 넣을 수 있게 다듬는다.
    // 로그인 실패 때의 loginId는 회원가입 형식 검사를 거치지 않은, 사용자가 보낸 값 그대로다.
    // 줄바꿈이 섞여 있으면 "로그인 성공 loginId=admin" 같은 가짜 로그 줄을 끼워 넣을 수 있어서,
    // 줄바꿈을 지우고 길이도 20자로 자른다.
    private String forLog(String loginId) {
        if (loginId == null) {
            return "";
        }
        String oneLine = loginId.replaceAll("[\\r\\n]", "");
        return oneLine.length() > 20 ? oneLine.substring(0, 20) + "…" : oneLine;
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
        // 토큰 값은 로그에 남기지 않는다. 누가 로그아웃했는지는 MDC의 user로 알 수 있다(액세스 토큰을 같이 보낸 경우).
        log.info("로그아웃");
    }
}
