package com.soksak.soksak.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(String loginId);

    /**
     * 마디 차감 — 읽고-빼고-쓰기가 아니라 <b>조건부 UPDATE 한 방</b>이다.
     * <p>
     * 영향 행 수가 곧 판정이다: 1이면 차감 성공, 0이면 잔액 부족. 동시에 두 요청이 들어와도
     * 잔액이 음수로 내려가지 않는다 — 방 락은 방 단위라 이걸 대신해 주지 못한다.
     * <p>
     * ⚠️ {@code clearAutomatically}는 일부러 끈 채로 둔다. 켜면 영속성 컨텍스트가 비워져
     * 같은 트랜잭션에서 미리 읽어 둔 방·캐릭터가 detached가 되고 지연 로딩이 터진다.
     * 대신 갱신된 잔액은 {@link #findCreditBalanceById}로 다시 읽는다(스칼라라 1차 캐시를 안 탄다).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.creditBalance = u.creditBalance - :cost " +
            "WHERE u.id = :id AND u.creditBalance >= :cost")
    int deductCredit(@Param("id") Long id, @Param("cost") int cost);

    /** 지급. 위로는 제한이 없어 조건이 붙지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.creditBalance = u.creditBalance + :amount WHERE u.id = :id")
    int grantCredit(@Param("id") Long id, @Param("amount") int amount);

    @Query("SELECT u.creditBalance FROM User u WHERE u.id = :id")
    Optional<Integer> findCreditBalanceById(@Param("id") Long id);

    @Query("SELECT u.creditBalance FROM User u WHERE u.loginId = :loginId")
    Optional<Integer> findCreditBalanceByLoginId(@Param("loginId") String loginId);
}
