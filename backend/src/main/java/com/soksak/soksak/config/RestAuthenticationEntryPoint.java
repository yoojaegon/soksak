package com.soksak.soksak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 인증되지 않은 요청이 보호 자원에 접근할 때 스프링 시큐리티 기본 403 대신
// 앱 공통 에러 응답(ErrorResponse) 형태의 401 JSON을 내려준다.
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 이 경로는 GlobalExceptionHandler를 타지 않는다 — 여기서 안 찍으면 401은 서버 로그에
        // 단 한 줄도 안 남아, 응답 헤더의 traceId를 들고 와도 찾을 대상이 없다.
        log.warn("401 {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(ErrorCode.INVALID_TOKEN, request.getRequestURI());

        response.setStatus(ErrorCode.INVALID_TOKEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
