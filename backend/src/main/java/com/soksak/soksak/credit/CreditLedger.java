package com.soksak.soksak.credit;

import com.soksak.soksak.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마디 변동 내역 한 줄.
 * <p>
 * 잔액의 <b>진실은 {@code users.credit_balance}</b>이고 이 테이블은 내역이다. 잔액을 원장의
 * {@code SUM()}으로 구하지 않는 이유는 차감이 원자적이어야 하기 때문 — 집계로는 "읽고 더해서
 * 쓰는" 사이에 다른 요청이 낀다. 대신 원장이 없으면 "왜 줄었는지"를 못 보여준다. 그래서 둘 다
 * 두고 <b>같은 트랜잭션에서</b> 갱신한다.
 * <p>
 * 불변식: {@code SUM(delta) == users.credit_balance} (사용자별). 깨졌다면 차감과 원장이 다른
 * 트랜잭션에서 돌았다는 뜻이다.
 */
@Entity
@Table(
        name = "credit_ledger",
        indexes = @Index(name = "idx_credit_ledger_user_created", columnList = "user_id, created_at")
)
@NoArgsConstructor
@Getter
public class CreditLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    /**
     * 누구의 마디인지.
     * <p>
     * ⛔ <b>{@code chatRoomId}와 같은 이유로 FK를 걸지 않는다.</b> 회원 탈퇴를 만들면 FK가
     * "원장을 먼저 지우라"고 막거나 cascade로 같이 지워버리는데, 방이 없어져도 기록은 남아야
     * 한다면 사람이 나가도 마찬가지다. 회계는 대상이 사라져도 남는 게 본분이다.
     * <p>
     * 대신 DB가 잘못된 id를 막아 주지 않는다 — 넣는 자리가 {@code CreditService} 하나뿐이라
     * 그쪽을 좁게 유지하는 것으로 갚는다.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 음수면 차감, 양수면 지급. 한 컬럼이 양방향을 다 표현한다. */
    @Column(name = "delta", nullable = false)
    private int delta;

    /** 이 줄이 적용된 직후의 잔액. 없으면 "그때 얼마였나"를 SUM으로 역산해야 하고, 잔액과 원장이
     *  어긋났을 때 <b>어느 줄에서 갈렸는지</b>를 찾을 수 없다. */
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    /**
     * ⚠️ <b>{@code columnDefinition}을 직접 준다.</b> 안 주면 Hibernate가 <b>테이블을 만들 때</b>
     * 그 시점의 enum 값 목록으로 {@code credit_ledger_reason_check}를 박는데, 나중에 사유를
     * 추가해도 {@code ddl-auto: update}는 <b>기존 제약을 갱신하지 않는다.</b> 그러면 새 사유가
     * 들어가는 순간 23514로 터진다 — 2026-09-23에 {@code TOPUP}을 더하다 실제로 밟았다.
     * <p>
     * ⚠️ 테스트는 이 사고를 못 잡는다. H2 + {@code create-drop}이라 매번 현재 값으로 제약이
     * 새로 만들어진다 — <b>기존 테이블에서만</b> 나는 고장이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, columnDefinition = "varchar(20)")
    private CreditReason reason;

    /**
     * 차감이면 어느 방에서 썼는지. 지급이면 null.
     * <p>
     * ⛔ <b>FK를 걸지 않는다.</b> 걸면 방을 지울 때 cascade로 회계 기록이 같이 사라진다 —
     * 방은 없어져도 "그때 이 방에서 3마디 썼다"는 남아야 한다. 그래서 연관관계가 아니라 숫자다.
     */
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    /** 수동 지급 사유 등. 자동 경로에서는 비워 둔다. */
    @Column(name = "memo", length = 200)
    private String memo;

    @Builder
    public CreditLedger(Long userId, int delta, int balanceAfter, CreditReason reason,
                        Long chatRoomId, String memo) {
        this.userId = userId;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.chatRoomId = chatRoomId;
        this.memo = memo;
    }
}
