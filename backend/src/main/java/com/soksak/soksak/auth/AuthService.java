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
            // 실패는 warn — 한 아이디에 연속으로 쌓이면 그게 공격 신호다.
            // ⚠️ 비밀번호는 틀린 값이라도 절대 찍지 않는다(오타가 곧 다른 계정의 진짜 비번인 경우가 많다).
            log.warn("로그인 실패 loginId={}", forLog(request.loginId()));
            throw e;
        }

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

        log.info("로그인 성공 loginId={} userId={}", user.getLoginId(), user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * 로그 한 줄에 실을 수 있게 다듬는다.
     * <p>
     * 로그인 <b>실패</b> 경로의 loginId는 존재하는 계정도 아니고 형식 제약도 없는 순수 입력값이다
     * (회원가입의 `^[a-z0-9]{4,20}$`는 여기 안 걸린다). 개행이 섞이면 로그에 가짜 줄을 통째로
     * 심을 수 있어 — "로그인 성공 loginId=admin" 같은 걸 — 개행을 걷어내고 길이도 자른다.
     */
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
        // 토큰 값은 남기지 않는다. 누구인지는 MDC의 user가 알려준다(액세스 토큰이 함께 온 경우).
        log.info("로그아웃");
    }
}
