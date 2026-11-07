package com.soksak.soksak;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 테스트용 Postgres 컨테이너. 운영(RDS)과 같은 메이저 버전을 쓴다.
 * 부분 인덱스나 CHECK 제약처럼 Postgres에만 있는 것도 마이그레이션 그대로 테스트에 들어온다.
 * 컨테이너는 Spring 컨텍스트마다 하나씩 뜨고, 컨텍스트 캐시가 살아 있는 동안 재사용된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
