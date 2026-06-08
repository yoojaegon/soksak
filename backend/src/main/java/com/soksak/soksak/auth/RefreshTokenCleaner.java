package com.soksak.soksak.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(cron = " 0 0 4 * * *")
    public void cleanExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        log.info("만료된 refresh 토큰 {}건 정리", deleted);
    }
}
