package com.soksak.soksak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatStreamConfig {

    // SseEmitter 스트리밍 작업을 요청 스레드 밖에서 돌리기 위한 전용 풀.
    // 각 작업은 AI 응답이 끝날 때까지 스레드를 점유하므로, 상한 16은 "서버 전체에서 동시에
    // 진행 중인 스트림 개수"(≈ 동시 스트리밍 사용자 수)를 제한한다. 방 락과는 별개다 —
    // 초과분은 큐에서 앞 작업이 끝날 때까지 대기하니, 동시 접속 규모가 커지면 이 값을 올려야 한다.
    // destroyMethod=shutdown: 컨텍스트 종료 시 풀도 정리한다.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return Executors.newFixedThreadPool(16);
    }
}