package com.soksak.soksak.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스트림 실행기의 세 가지 약속을 고정한다.
 * MDC를 실어 나르는 것, 상한을 넘으면 기다리지 않고 거절하는 것, 종료 때 진행 중인 작업을 기다리는 것.
 * <p>
 * MDC가 깨지면 traceId가 스트리밍 구간에서만 빈칸이 되는데, 예외도 경고도 없이 조용히 그렇게 된다.
 * 거절과 종료 대기도 설정 한 줄이 빠지면 조용히 "큐에서 대기"나 "바로 끊김"으로 바뀐다.
 */
class ChatStreamConfigTest {

    private final SimpleAsyncTaskExecutor executor = new ChatStreamConfig().chatStreamExecutor();

    @AfterEach
    void tearDown() {
        executor.close();
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

    // SSE가 정확히 이 모양이다. 요청 스레드는 스트림을 제출하자마자 돌아가 필터 finally에서
    // MDC를 비우고, 작업은 그 뒤에도 한참 돈다. 실행 시점에 읽으면 이미 늦다.
    @Test
    @DisplayName("제출 뒤 요청 스레드가 MDC를 비워도 작업은 그대로 들고 간다")
    void copies_at_submit_time_not_at_run_time() throws Exception {
        MDC.put(TraceIdFilter.TRACE_ID, "abc12345");

        CountDownLatch gate = new CountDownLatch(1);
        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> {
            await(gate);
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

    @Test
    @DisplayName("작업은 가상 스레드에서 돈다")
    void runs_on_a_virtual_thread() throws Exception {
        CompletableFuture<Thread> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(Thread.currentThread()));

        Thread worker = seen.get(2, TimeUnit.SECONDS);
        assertThat(worker.isVirtual()).isTrue();
        assertThat(worker.getName()).startsWith("chat-stream-");
    }

    // 거절하지 않으면 execute가 요청 스레드를 붙잡고 자리가 날 때까지 기다린다.
    // MessageService는 이 예외를 받아 스트림을 열기 전에 SERVER_BUSY로 돌려준다.
    @Test
    @DisplayName("상한만큼 돌고 있으면 다음 작업은 기다리지 않고 거절된다")
    void rejects_immediately_when_the_limit_is_reached() throws Exception {
        CountDownLatch gate = fillUpToLimit();
        try {
            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class);
        } finally {
            gate.countDown();
        }
    }

    @Test
    @DisplayName("작업이 끝나면 자리가 돌아와 다시 받는다")
    void accepts_again_after_running_tasks_finish() throws Exception {
        CountDownLatch gate = fillUpToLimit();
        gate.countDown();

        // 자리는 작업이 끝난 직후 finally에서 반납된다. 그 순간을 밖에서 잡을 수 없어 잠깐 재시도한다.
        CompletableFuture<Boolean> ran = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (true) {
            try {
                executor.execute(() -> ran.complete(true));
                break;
            } catch (TaskRejectedException e) {
                assertThat(System.nanoTime()).as("2초 안에 자리가 돌아와야 한다").isLessThan(deadline);
                Thread.sleep(10);
            }
        }

        assertThat(ran.get(2, TimeUnit.SECONDS)).isTrue();
    }

    // 배포로 컨테이너가 내려갈 때 진행 중인 스트림이 저장과 환불까지 마치게 하는 장치다.
    @Test
    @DisplayName("닫을 때 진행 중인 작업이 끝날 때까지 기다린다")
    void waits_for_running_tasks_on_close() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            finished.set(true);
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        executor.close();

        assertThat(finished).isTrue();
    }

    @Test
    @DisplayName("닫힌 뒤에 들어온 작업은 거절된다")
    void rejects_after_close() {
        executor.close();

        assertThatThrownBy(() -> executor.execute(() -> { }))
                .isInstanceOf(TaskRejectedException.class);
    }

    // 상한만큼 작업을 띄워 전부 돌고 있는 상태를 만든다. 돌려받은 latch를 열면 모두 끝난다.
    private CountDownLatch fillUpToLimit() throws InterruptedException {
        int limit = executor.getConcurrencyLimit();
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            executor.execute(() -> {
                running.countDown();
                await(gate);
            });
        }
        assertThat(running.await(2, TimeUnit.SECONDS)).isTrue();
        return gate;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
