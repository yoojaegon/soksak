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

/*
  요청마다 traceId를 붙여서 같은 요청의 로그끼리 묶어 볼 수 있게 한다.
  채팅은 SSE와 AI 호출이 섞여서 로그가 여러 줄로 흩어지기 때문에,
  이게 없으면 동시 사용자가 둘만 돼도 어떤 로그가 어떤 요청인지 구분이 안 된다.

  시큐리티 필터보다 먼저 실행되도록 순서를 가장 앞으로 뒀다.
  그래야 인증에서 막힌 401/403 요청에도 traceId가 남는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String USER = "user";        // 인증 성공 시 JwtFilter에서 채움

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // UUID 전체는 너무 길어서 앞 8자리만 사용.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        // 응답 헤더에도 넣어서, 사용자가 에러를 제보할 때 이 값으로 로그를 바로 찾을 수 있게 함.
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // SSE 스트림 스레드는 여기서 MDC를 비운 뒤에도 계속 돌지만,
            // 작업을 넘길 때 MDC를 복사해 가기 때문에(ChatStreamConfig) 상관없다.
            MDC.clear();
        }
    }
}