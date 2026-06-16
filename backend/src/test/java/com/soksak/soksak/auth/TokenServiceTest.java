package com.soksak.soksak.auth;

import com.soksak.soksak.auth.dto.TokenResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.config.jwt.JwtProperties;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtProperties jwtProperties;

    @InjectMocks TokenService tokenService;

    @Test
    @DisplayName("유효한 refresh로 재발급하면 새 access·refresh를 주고, 저장된 토큰을 회전시킨다")
    void reissue_success() {
        // given
        String oldRefresh = "old-refresh";
        RefreshToken stored = mock(RefreshToken.class);
        Authentication auth = mock(Authentication.class);

        when(tokenProvider.validToken(oldRefresh)).thenReturn(true);
        when(refreshTokenService.findByRefreshToken(oldRefresh)).thenReturn(stored);
        when(tokenProvider.getAuthentication(oldRefresh)).thenReturn(auth);
        when(tokenProvider.generateAccessToken(auth)).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(auth)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(1209600000L); // 14일(ms)

        // when
        TokenResponse response = tokenService.reissue(oldRefresh);

        // then
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        // 저장된 row가 새 refresh로 회전됐는지(폐기+갱신) 검증
        verify(stored).rotate(eq("new-refresh"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("유효하지 않은 refresh면 예외를 던지고, DB 조회까지 가지 않는다")
    void reissue_invalidToken() {
        // given
        String badRefresh = "bad-refresh";
        when(tokenProvider.validToken(badRefresh)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(badRefresh))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenService, never()).findByRefreshToken(any());
    }
}