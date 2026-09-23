package com.soksak.soksak.credit;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마디(소모 재화) 차감·지급.
 * <p>
 * ⚠️ <b>별도 빈이어야 한다.</b> {@code MessageService} 안의 메서드로 두면 self-invocation이라
 * {@code @Transactional}이 안 걸린다 — {@code ChatTxService}를 뺀 것과 같은 이유다.
 * <p>
 * ⚠️ <b>방 락({@code withRoomLock})은 이 문제를 못 막는다.</b> 락은 {@code roomId} 단위라
 * 다른 방 두 개로 동시에 보내면 그대로 통과한다. 계정 단위 보호는 아래 원자적 UPDATE 하나뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final CreditLedgerRepository creditLedgerRepository;

    /**
     * 스트림을 시작하기 <b>전에</b> 잔액을 미리 보는 용도.
     * <p>
     * ⚠️ <b>이건 가드가 아니다.</b> 여기서 읽고 실제 차감까지 사이에 다른 요청이 낄 수 있다.
     * 진짜 보호는 {@link #charge}의 원자적 UPDATE고, 이 메서드는 SSE가 열리기 전에 평범한
     * 402 JSON으로 돌려보내기 위한 UX 장치다(스트림이 시작된 뒤엔 프론트가 에러를 다르게 다뤄야 한다).
     */
    @Transactional(readOnly = true)
    public void ensureAffordable(Long userId, int cost) {
        int balance = userRepository.findCreditBalanceById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (balance < cost) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT);
        }
    }

    /**
     * 차감. 잔액이 모자라면 {@link ErrorCode#INSUFFICIENT_CREDIT}을 던진다.
     * <p>
     * 호출자의 트랜잭션에 참여한다({@code REQUIRED}) — USER 메시지 저장과 한 트랜잭션이어야
     * "보냈는데 안 깎였다"나 그 반대가 안 생긴다.
     */
    @Transactional
    public void charge(User user, int cost, CreditReason reason, Long chatRoomId) {
        if (cost <= 0) {
            return;     // 무료 경로(계수 0). 원장에 0짜리 줄을 남기면 내역만 지저분해진다.
        }
        // 읽고-빼고-쓰기가 아니라 조건부 UPDATE 한 방이다. 영향 행 수가 곧 성공 여부라
        // 동시 요청이 겹쳐도 잔액이 음수로 내려가지 않는다.
        int affected = userRepository.deductCredit(user.getId(), cost);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CREDIT);
        }
        record(user.getId(), -cost, reason, chatRoomId, null);
    }

    /**
     * 답이 끝내 안 나왔을 때 되돌려준다. <b>차감 줄을 지우지 않고 반대 부호의 줄을 더한다</b> —
     * 회계 기록을 고쳐 쓰면 "원래 얼마를 받았나"를 잃는다.
     * <p>
     * 차감 트랜잭션은 이미 커밋된 뒤(스트림이 그제서야 실패한다)라 롤백으로는 못 되돌린다.
     * 그래서 별도 트랜잭션의 지급으로 상쇄하는 모양이다.
     * <p>
     * ⚠️ 엔티티가 아니라 {@code userId}를 받는다 — 호출자(스트림 스레드)가 들고 있는 유저는 이미
     * 닫힌 영속성 컨텍스트의 것이다. 원장이 FK가 아니라 숫자를 들고 있어서 다시 읽을 필요도 없다.
     */
    @Transactional
    public void refund(Long userId, int amount, Long chatRoomId) {
        if (amount <= 0) {
            return;
        }
        userRepository.grantCredit(userId, amount);
        record(userId, amount, CreditReason.REFUND, chatRoomId, null);
    }

    /** 지급(가입 보너스·수동). 실패 경로가 없다 — 잔액은 위로는 제한이 없다. */
    @Transactional
    public void grant(User user, int amount, CreditReason reason, String memo) {
        if (amount <= 0) {
            return;
        }
        userRepository.grantCredit(user.getId(), amount);
        record(user.getId(), amount, reason, null, memo);
    }

    /**
     * 충전 — 묶음을 골라 <b>결제 없이</b> 바로 지급한다. 지급 후 잔액을 돌려준다.
     * <p>
     * 수량의 출처는 {@link CreditPackCatalog} 하나다. 요청이 {@code packId}만 나르므로 프론트가
     * 숫자를 지어내도 통하지 않는다.
     * <p>
     * ⚠️ {@code grant}를 직접 부르는 self-invocation이라 그쪽 {@code @Transactional}은 안 걸린다 —
     * 이 메서드가 트랜잭션을 여는 것으로 대신한다. 지급과 원장이 한 트랜잭션이어야 한다는 조건은
     * 그대로 지켜진다.
     */
    @Transactional
    public int topUp(String loginId, String packId) {
        CreditPackCatalog.Pack pack = CreditPackCatalog.byId(packId);
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        grant(user, pack.amount(), CreditReason.TOPUP, pack.id());
        return userRepository.findCreditBalanceById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public int balanceOf(String loginId) {
        return userRepository.findCreditBalanceByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 원장 한 줄. 잔액은 방금 돌린 UPDATE의 결과를 <b>스칼라 조회로 다시 읽는다.</b>
     * <p>
     * ⚠️ {@code user.getCreditBalance()}를 쓰면 안 된다 — 벌크 UPDATE는 영속성 컨텍스트를
     * 거치지 않으므로 그 엔티티에는 <b>옛 잔액</b>이 그대로 남아 있다. 스칼라 프로젝션은 1차
     * 캐시를 타지 않아 DB의 값을 그대로 가져온다.
     * <p>
     * ⚠️ 같은 이유로 리포지터리 쪽에 {@code clearAutomatically}를 켜지 않았다 — 컨텍스트를
     * 비우면 같은 트랜잭션에서 미리 읽어 둔 방·캐릭터가 detached가 되어 지연 로딩이 터진다.
     */
    private void record(Long userId, int delta, CreditReason reason, Long chatRoomId, String memo) {
        int balanceAfter = userRepository.findCreditBalanceById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        creditLedgerRepository.save(CreditLedger.builder()
                .userId(userId)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .reason(reason)
                .chatRoomId(chatRoomId)
                .memo(memo)
                .build());

        log.debug("마디 {} {} 잔액={} reason={}", delta > 0 ? "지급" : "차감", Math.abs(delta),
                balanceAfter, reason);
    }
}
