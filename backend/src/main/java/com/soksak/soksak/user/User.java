package com.soksak.soksak.user;

import com.soksak.soksak.common.BaseTimeEntity;
import com.soksak.soksak.common.Gender;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "age", nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    /**
     * 마디 잔액 — <b>이 값이 진실</b>이고 {@code credit_ledger}는 내역이다.
     * <p>
     * 새 유저는 0으로 태어나고 가입 보너스가 원장을 거쳐 올려준다(그래야
     * {@code SUM(delta) == credit_balance} 불변식이 처음부터 성립한다).
     * <p>
     * ⚠️ 갱신은 {@code UserRepository}의 벌크 UPDATE로만 한다 — 이 필드에 setter를 만들어
     * 더티 체킹으로 쓰면 "읽고 빼서 쓰는" 모양이 되어 동시 요청에서 잔액이 음수가 된다.
     * 그래서 벌크 UPDATE 직후 이 엔티티의 값은 <b>낡아 있다</b>(스칼라 조회로 다시 읽을 것).
     * <p>
     * ⚠️ <b>배포 전에 손으로 돌려야 하는 SQL이 있다.</b> {@code ddl-auto: update}는 기존 행이
     * 있는 테이블에 NOT NULL 컬럼을 못 만드는데, Hibernate는 그 실패를 로그만 찍고 기동을
     * 계속하므로 <b>컬럼 없이 떠서 {@code users} 쿼리가 전부 깨진다.</b> Postgres는 DEFAULT가
     * 있으면 붙일 수 있다:
     * <pre>
     * ALTER TABLE users ADD COLUMN credit_balance INTEGER NOT NULL DEFAULT 0;
     * </pre>
     * 그리고 이대로 두면 기존 계정이 전원 0마디라 모든 전송이 402다. 새 코드가 떠서
     * {@code credit_ledger}가 생긴 뒤에 소급 지급까지 해야 끝난다 — 잔액만 올리면
     * {@code SUM(delta) == credit_balance} 불변식이 첫날부터 깨지므로 원장 줄도 같이 넣는다.
     * 전체 절차는 메모리의 {@code todos.md} A9 "배포 절차"에 있다.
     */
    @Column(name = "credit_balance", nullable = false)
    private int creditBalance;

    @Builder
    public User(String email, String loginId, String nickname, String password,
                int age, Gender gender) {
        this.email = email;
        this.loginId = loginId;
        this.nickname = nickname;
        this.password = password;
        this.age = age;
        this.gender = gender;
    }
}
