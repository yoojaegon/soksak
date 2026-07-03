package com.soksak.soksak.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final static String TOKEN_PREFIX = "Bearer ";
    private final static String HEADER_AUTHORIZATION = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                         HttpServletResponse response,
                         FilterChain filterChain) throws ServletException, IOException {
        // 요청 헤더의 키 값 조회
        String header = request.getHeader(HEADER_AUTHORIZATION);
        String token = null;
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            token = header.substring(TOKEN_PREFIX.length());   // "Bearer " 뒤만
        }

        if (token != null) {
            // 서명 검증+파싱을 한 번만 하고, 그 Claims로 타입 확인·인증 복원까지 재사용한다.
            jwtTokenProvider.parse(token)
                    .filter(claims -> JwtTokenProvider.TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(claims)))
                    .ifPresent(claims -> SecurityContextHolder.getContext()
                            .setAuthentication(jwtTokenProvider.getAuthentication(claims)));
        }

        filterChain.doFilter(request, response);
    }
}
