package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtProperties jwtProperties;

    @InjectMocks TokenService tokenService;

    @Test
    @DisplayName("유효한 refresh로 재발급하면 새 access·refresh를 주고, 저장된 토큰을 원자적으로 회전시킨다")
    void reissue_success() {
        // given
        String oldRefresh = "old-refresh";
        Authentication auth = mock(Authentication.class);
        Claims claims = mock(Claims.class);

        when(tokenProvider.parse(oldRefresh)).thenReturn(Optional.of(claims));
        when(tokenProvider.getTokenType(claims)).thenReturn(JwtTokenProvider.TYPE_REFRESH);
        when(tokenProvider.getAuthentication(claims)).thenReturn(auth);
        when(tokenProvider.generateAccessToken(auth)).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(auth)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(1209600000L); // 14일(ms)
        // 원자 회전이 정확히 1행을 갱신 → 재발급 성공
        when(refreshTokenRepository.rotate(eq(oldRefresh), eq("new-refresh"), any(LocalDateTime.class)))
                .thenReturn(1);

        // when
        TokenResponse response = tokenService.reissue(oldRefresh);

        // then
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        // 저장된 row가 새 refresh로 원자 회전됐는지 검증
        verify(refreshTokenRepository).rotate(eq(oldRefresh), eq("new-refresh"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("refresh 타입이 아니면(또는 서명 무효) 예외를 던지고, 회전까지 가지 않는다")
    void reissue_invalidToken() {
        // given
        String badRefresh = "bad-refresh";
        when(tokenProvider.parse(badRefresh)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(badRefresh))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenRepository, never()).rotate(any(), any(), any());
    }

    @Test
    @DisplayName("회전이 0행이면(이미 회전됨=재사용/경합 패배) 예외를 던진다")
    void reissue_reusedToken() {
        // given
        String oldRefresh = "already-rotated";
        Authentication auth = mock(Authentication.class);
        Claims claims = mock(Claims.class);

        when(tokenProvider.parse(oldRefresh)).thenReturn(Optional.of(claims));
        when(tokenProvider.getTokenType(claims)).thenReturn(JwtTokenProvider.TYPE_REFRESH);
        when(tokenProvider.getAuthentication(claims)).thenReturn(auth);
        when(tokenProvider.generateAccessToken(auth)).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(auth)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(1209600000L);
        // 이미 회전돼 old 토큰이 DB에 없음 → 0행
        when(refreshTokenRepository.rotate(eq(oldRefresh), eq("new-refresh"), any(LocalDateTime.class)))
                .thenReturn(0);

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(oldRefresh))
                .isInstanceOf(BusinessException.class);
    }
}