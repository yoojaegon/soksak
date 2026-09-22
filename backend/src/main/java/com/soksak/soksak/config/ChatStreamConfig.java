package com.soksak.soksak.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ChatStreamConfig {

    // SseEmitter 스트리밍 작업을 요청 스레드 밖에서 돌리기 위한 전용 풀.
    // 각 작업은 AI 응답이 끝날 때까지 스레드를 점유하므로, 상한 16은 "서버 전체에서 동시에
    // 진행 중인 스트림 개수"(≈ 동시 스트리밍 사용자 수)를 제한한다. 방 락과는 별개다 —
    // 초과분은 큐에서 앞 작업이 끝날 때까지 대기하니, 동시 접속 규모가 커지면 이 값을 올려야 한다.
    // destroyMethod=shutdown: 컨텍스트 종료 시 풀도 정리한다.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return new MdcPropagatingExecutor(16);
    }

    /**
     * 제출한 스레드의 MDC를 작업 스레드로 실어 나르는 풀.
     * <p>
     * MDC는 ThreadLocal이라 그냥 두면 traceId가 <b>스트리밍 구간에서만 빈칸</b>이 된다 —
     * 정작 traceId가 필요한 유일한 구간이 거기다. 게다가 조용히 비기 때문에(예외도, 경고도 없다)
     * 설정이 다 된 줄 알고 넘어가기 쉽다. 여기서 한 번 감싸면 호출부는 몰라도 된다.
     * <p>
     * {@code submit}·{@code invokeAll}도 결국 {@link ThreadPoolExecutor#execute}로 모이므로
     * 이 메서드 하나만 덮으면 모든 제출 경로가 덮인다.
     */
    private static class MdcPropagatingExecutor extends ThreadPoolExecutor {

        MdcPropagatingExecutor(int threads) {
            super(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public void execute(Runnable task) {
            // 제출 '시점'에 떠야 한다. 작업이 실제로 도는 시점엔 요청 스레드가 이미 MDC를 비운 뒤다.
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            super.execute(() -> {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                try {
                    task.run();
                } finally {
                    // 풀 스레드는 재사용된다 — 안 지우면 다음 작업이 남의 traceId를 달고 찍힌다.
                    MDC.clear();
                }
            });
        }
    }
}
