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

    // 매일 새벽 4시에 만료된 리프레시 토큰을 지운다.
    // 만료된 토큰은 어차피 재발급 때 거부되므로, DB에 쌓이지 않게 치우는 용도다.
    @Transactional
    @Scheduled(cron = " 0 0 4 * * *")
    public void cleanExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        log.info("만료된 refresh 토큰 {}건 정리", deleted);
    }
}
