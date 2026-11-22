package com.soksak.soksak.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

@Configuration
public class ChatStreamConfig {

    // 서버 전체에서 동시에 진행할 수 있는 스트림 개수.
    // ai-server의 스레드풀(40)을 /chat, /summarize와 나눠 쓰므로 그보다 낮게 둔다.
    private static final int MAX_CONCURRENT_STREAMS = 30;

    // 종료할 때 진행 중인 스트림이 저장과 환불까지 마치도록 기다려 주는 시간.
    // docker-compose.yml의 backend.stop_grace_period가 이보다 길어야 한다.
    private static final long TERMINATION_TIMEOUT_MS = 60_000L;

    // SseEmitter 스트리밍 작업을 요청 스레드 밖에서 돌리는 실행기.
    // 작업마다 가상 스레드를 새로 만든다. 스트림은 대부분의 시간을 AI 응답을 기다리며 보내므로
    // 플랫폼 스레드를 풀에 묶어 둘 이유가 없다.
    // 상한을 넘으면 대기시키지 않고 TaskRejectedException을 던진다.
    // 아직 스트림이 열리기 전이라 호출부가 일반 에러 응답으로 거절할 수 있다.
    // AutoCloseable이라 컨텍스트가 닫힐 때 Spring이 close()를 불러 위 시간만큼 기다린다.
    @Bean
    public SimpleAsyncTaskExecutor chatStreamExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("chat-stream-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(MAX_CONCURRENT_STREAMS);
        executor.setRejectTasksWhenLimitReached(true);
        executor.setTaskTerminationTimeout(TERMINATION_TIMEOUT_MS);
        executor.setTaskDecorator(mdcPropagatingDecorator());
        return executor;
    }

    // 제출한 스레드의 MDC를 작업 스레드로 실어 나른다.
    // MDC는 ThreadLocal이라 그냥 두면 스트리밍 구간의 로그에서만 traceId가 빈다.
    // decorate는 execute를 부른 요청 스레드에서 실행되므로 복사는 제출 시점에 일어난다.
    // 작업이 실제로 도는 시점에는 요청 스레드가 이미 MDC를 비운 뒤라 그때 읽으면 늦다.
    // 가상 스레드는 작업이 끝나면 사라지므로 MDC를 따로 지우지 않는다.
    private static TaskDecorator mdcPropagatingDecorator() {
        return task -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                task.run();
            };
        };
    }
}
