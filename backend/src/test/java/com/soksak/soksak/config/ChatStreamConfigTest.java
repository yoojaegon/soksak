package com.soksak.soksak.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스트림 풀이 MDC를 실어 나르는지 고정한다.
 * <p>
 * 이게 깨지면 traceId가 <b>스트리밍 구간에서만</b> 빈칸이 되는데, 예외도 경고도 없이 조용히
 * 그렇게 된다 — 로그 설정이 다 된 줄 알고 넘어가기 딱 좋은 자리라 테스트로 묶어 둔다.
 */
class ChatStreamConfigTest {

    private final ExecutorService executor = new ChatStreamConfig().chatStreamExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MDC.clear();
    }

    @Test
    @DisplayName("제출한 스레드의 MDC가 작업 스레드로 따라간다")
    void carries_the_callers_mdc_into_the_worker() throws Exception {
        MDC.put(TraceIdFilter.TRACE_ID, "abc12345");

        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(MDC.get(TraceIdFilter.TRACE_ID)));

        assertThat(seen.get(2, TimeUnit.SECONDS)).isEqualTo("abc12345");
    }

    // SSE가 정확히 이 모양이다: 요청 스레드는 스트림을 제출하자마자 돌아가 필터 finally에서
    // MDC를 비우고, 작업은 그 뒤에도 한참 돈다. 실행 시점에 읽으면 이미 늦는다.
    @Test
    @DisplayName("제출 뒤 요청 스레드가 MDC를 비워도 작업은 그대로 들고 간다")
    void copies_at_submit_time_not_at_run_time() throws Exception {
        MDC.put(TraceIdFilter.TRACE_ID, "abc12345");

        CountDownLatch gate = new CountDownLatch(1);
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            seen.complete(MDC.get(TraceIdFilter.TRACE_ID));
        });

        MDC.clear();        // 요청 스레드가 응답을 반환하며 치우는 시점
        gate.countDown();   // 그 뒤에야 작업이 실제로 돈다

        assertThat(seen.get(2, TimeUnit.SECONDS)).isEqualTo("abc12345");
    }

    @Test
    @DisplayName("MDC 없이 제출하면 작업도 빈 채로 돈다")
    void leaves_the_worker_clean_when_there_is_nothing_to_carry() throws Exception {
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(MDC.get(TraceIdFilter.TRACE_ID)));

        assertThat(seen.get(2, TimeUnit.SECONDS)).isNull();
    }
}
