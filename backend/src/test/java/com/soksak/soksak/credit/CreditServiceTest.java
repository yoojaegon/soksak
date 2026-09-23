package com.soksak.soksak.credit;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마디 차감의 원자성과 원장 불변식을 고정한다.
 * <p>
 * 이 두 가지가 깨지면 증상이 조용하다 — 잔액이 음수가 되거나 원장과 어긋나도 대화는 멀쩡히
 * 돌아가고, 한참 뒤에 "왜 마이너스지"로 발견된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditServiceTest {

    @Autowired CreditService creditService;
    @Autowired UserRepository userRepository;
    @Autowired CreditLedgerRepository creditLedgerRepository;

    private User user;

    @BeforeEach
    void setUp() {
        // ⚠️ users는 비우지 않는다 — 다른 E2E 클래스가 남긴 캐릭터가 user_id로 참조하고 있어
        // deleteAll이 FK로 터진다. 대신 매번 새 계정을 만들고 원장만 비워 이 테스트의 줄만 남긴다.
        creditLedgerRepository.deleteAll();
        String unique = "credit-" + UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .loginId(unique).email(unique + "@soksak.com").nickname(unique)
                .password("pw").age(30).gender(Gender.MALE)
                .build());
    }

    @Test
    @DisplayName("지급하면 잔액이 오르고 원장에 양수 한 줄이 남는다")
    void grant_raises_balance_and_records_a_positive_row() {
        creditService.grant(user, 100, CreditReason.SIGNUP_BONUS, "가입 보너스");

        assertThat(balance()).isEqualTo(100);
        assertThat(creditLedgerRepository.findAll()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getDelta()).isEqualTo(100);
                    assertThat(row.getBalanceAfter()).isEqualTo(100);
                    assertThat(row.getReason()).isEqualTo(CreditReason.SIGNUP_BONUS);
                });
    }

    @Test
    @DisplayName("차감하면 모델 계수만큼 줄고 원장의 balanceAfter가 그 시점 잔액을 남긴다")
    void charge_deducts_and_records_balance_after() {
        creditService.grant(user, 10, CreditReason.SIGNUP_BONUS, null);

        creditService.charge(user, 3, CreditReason.MESSAGE, 42L);

        assertThat(balance()).isEqualTo(7);
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.MESSAGE)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getDelta()).isEqualTo(-3);
                    assertThat(row.getBalanceAfter()).isEqualTo(7);
                    assertThat(row.getChatRoomId()).isEqualTo(42L);
                });
    }

    @Test
    @DisplayName("잔액보다 비싸면 INSUFFICIENT_CREDIT을 던지고 원장에도 아무것도 안 남는다")
    void charge_over_balance_throws_and_leaves_no_row() {
        creditService.grant(user, 2, CreditReason.SIGNUP_BONUS, null);

        assertThatThrownBy(() -> creditService.charge(user, 3, CreditReason.MESSAGE, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_CREDIT);

        assertThat(balance()).isEqualTo(2);
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.MESSAGE)
                .isEmpty();
    }

    // 방 락(withRoomLock)은 roomId 단위라 다른 방 두 개로 동시에 보내면 그대로 통과한다.
    // 계정 단위 보호는 조건부 UPDATE 하나뿐이고, 이 테스트가 그걸 고정한다.
    @Test
    @DisplayName("동시에 쏟아져도 잔액은 음수가 되지 않고 성공 횟수만큼만 깎인다")
    void concurrent_charges_never_overdraw() throws Exception {
        creditService.grant(user, 10, CreditReason.SIGNUP_BONUS, null);

        int threads = 20;                       // 10마디에 1마디씩 20번 → 10번만 성공해야 한다
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    startGate.await();
                    creditService.charge(user, 1, CreditReason.MESSAGE, 1L);
                    succeeded.incrementAndGet();
                } catch (BusinessException expected) {
                    // 잔액 부족 — 여기로 떨어지는 게 정상이다.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(balance()).isZero();
        assertThat(ledgerSum()).isEqualTo(balance());   // 불변식
    }

    @Test
    @DisplayName("계수가 0이면 원장에 0짜리 줄을 남기지 않는다")
    void zero_cost_is_a_no_op() {
        creditService.grant(user, 5, CreditReason.SIGNUP_BONUS, null);

        creditService.charge(user, 0, CreditReason.MESSAGE, 1L);

        assertThat(balance()).isEqualTo(5);
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.MESSAGE)
                .isEmpty();
    }

    private int balance() {
        return userRepository.findCreditBalanceById(user.getId()).orElseThrow();
    }

    private int ledgerSum() {
        return creditLedgerRepository.findAll().stream().mapToInt(CreditLedger::getDelta).sum();
    }
}
