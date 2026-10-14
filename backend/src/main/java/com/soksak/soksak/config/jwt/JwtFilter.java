package com.soksak.soksak.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.soksak.soksak.config.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
        // Authorization 헤더가 "Bearer "로 시작할 때만 그 뒤를 토큰으로 꺼낸다.
        String header = request.getHeader(HEADER_AUTHORIZATION);
        String token = null;
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            token = header.substring(TOKEN_PREFIX.length());
        }

        if (token != null) {
            // 파싱은 한 번만 하고, 그 Claims로 토큰 종류 확인과 인증 복원까지 한다.
            // access 토큰만 통과시켜서 refresh 토큰으로는 API를 부를 수 없게 한다.
            jwtTokenProvider.parse(token)
                    .filter(claims -> JwtTokenProvider.TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(claims)))
                    .ifPresent(claims -> {
                        SecurityContextHolder.getContext()
                                .setAuthentication(jwtTokenProvider.getAuthentication(claims));
                        // 로그에 누구의 요청인지 남긴다. 요청이 끝나면 바깥의 TraceIdFilter가 지운다.
                        // ⚠️ 토큰 값은 절대 넣지 않는다. 로그를 본 사람이 그 토큰으로 로그인할 수 있게 된다.
                        MDC.put(TraceIdFilter.USER, claims.getSubject());
                    });
        }

        // 토큰이 없거나 틀려도 여기서 막지 않고 비로그인 상태로 넘긴다.
        // 막을지는 SecurityConfig의 접근 규칙이 정한다.
        filterChain.doFilter(request, response);
    }
}
