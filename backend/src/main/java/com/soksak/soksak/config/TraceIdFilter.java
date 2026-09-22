package com.soksak.soksak.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 하나에 식별자를 붙여 로그를 이어붙일 수 있게 한다.
 * <p>
 * 채팅은 SSE + AI 호출이 얽혀 있어 한 요청의 로그가 여러 줄로 흩어지는데, 이 값이 없으면
 * 동시 사용자가 둘만 돼도 어느 줄이 어느 요청인지 가를 수 없다.
 * <p>
 * HIGHEST_PRECEDENCE로 시큐리티 체인보다 바깥에 선다 — 인증에서 잘린 401/403 요청도
 * 덮어야 "누가 언제 막혔는지"가 남기 때문이다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String USER = "user";        // JwtFilter가 인증에 성공하면 채운다.

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // UUID 전체는 로그 한 줄을 다 잡아먹는다. 앞 8자리면 동시 요청을 가르기에 충분하다.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        // 사용자가 "에러 났어요"라고 할 때 이 값을 받으면 로그에서 바로 찾을 수 있다.
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // SSE는 여기서 MDC가 비워진 뒤에도 스트림 스레드가 계속 도는데, 그쪽은 제출 시점에
            // 복사본을 떠 가므로(ChatStreamConfig) 영향받지 않는다.
            MDC.clear();
        }
    }
}