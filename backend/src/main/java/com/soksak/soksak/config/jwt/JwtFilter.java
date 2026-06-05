package com.soksak.soksak.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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

        if (token != null && jwtTokenProvider.validToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
