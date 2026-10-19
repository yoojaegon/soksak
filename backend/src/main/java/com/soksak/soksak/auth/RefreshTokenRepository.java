package com.soksak.soksak.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUserId(Long userId);
    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteAllByExpiresAtBefore(@Param("now")LocalDateTime now);

    // 옛 토큰이 DB에 있을 때만 새 토큰으로 바꾼다.
    // 바뀐 줄 수(0 또는 1)를 보고 이미 쓰인 토큰인지 판단한다(TokenService.reissue).
    @Modifying()
    @Query("update RefreshToken r set r.refreshToken = :newToken, r.expiresAt = :expiresAt " + "where r.refreshToken = :oldToken")
    int rotate(@Param("oldToken") String oldToken,
               @Param("newToken") String newToken,
               @Param("expiresAt") LocalDateTime expiresAt);
}
