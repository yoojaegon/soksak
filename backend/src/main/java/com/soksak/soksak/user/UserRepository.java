package com.soksak.soksak.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    /**
     * 마디 차감. 읽고 빼서 쓰지 않고 조건부 UPDATE 한 번으로 처리한다.
     * 영향 행 수가 1이면 차감 성공, 0이면 잔액 부족이다.
     * 같은 유저의 요청이 동시에 와도 잔액이 음수가 되지 않는다.
     * 채팅방 락은 방마다 따로 걸려서, 다른 방에서 동시에 보내는 경우는 이 조건이 막는다.
     *
     * clearAutomatically는 일부러 끈다. 켜면 영속성 컨텍스트가 비워져서
     * 같은 트랜잭션에서 먼저 읽은 방과 캐릭터가 detached가 되고 지연 로딩이 실패한다.
     * 갱신된 잔액은 findCreditBalanceById로 다시 읽는다. 스칼라 조회라 1차 캐시를 거치지 않는다.
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
