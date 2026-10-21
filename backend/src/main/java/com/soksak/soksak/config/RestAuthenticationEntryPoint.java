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

// 로그인이 필요한 경로에 인증 없이 들어온 요청에 401 응답을 돌려준다.
// 응답 본문은 다른 에러와 같은 ErrorResponse JSON 모양으로 맞춘다.
// SecurityConfig의 exceptionHandling에 등록되어 있고, 스프링 시큐리티가 직접 호출한다.
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 이 응답은 필터 단계에서 나가서 GlobalExceptionHandler를 거치지 않는다.
        // 여기서 로그를 남기지 않으면 traceId로 401 요청을 찾을 수 없다.
        log.warn("401 {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(ErrorCode.INVALID_TOKEN, request.getRequestURI());

        response.setStatus(ErrorCode.INVALID_TOKEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
