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

    // 나이는 해마다 바뀌므로 출생연도를 저장하고 나이는 필요할 때 계산한다.
    @Column(name = "birth_year", nullable = false)
    private int birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    /**
     * 마디 잔액. 기준값은 이 컬럼이고 credit_ledger는 내역이다.
     * 새 유저는 0에서 시작하고, 가입 보너스도 원장을 거쳐 지급해서
     * SUM(delta) == credit_balance가 처음부터 맞게 한다.
     *
     * 갱신은 UserRepository의 벌크 UPDATE로만 한다.
     * setter로 읽고 빼서 쓰면 동시 요청에서 잔액이 음수가 될 수 있기 때문이다.
     * 벌크 UPDATE 뒤에는 이 필드 값이 갱신되지 않으니 잔액은 findCreditBalanceById로 다시 읽는다.
     */
    @Column(name = "credit_balance", nullable = false)
    private int creditBalance;

    @Builder
    public User(String email, String loginId, String nickname, String password,
                int birthYear, Gender gender) {
        this.email = email;
        this.loginId = loginId;
        this.nickname = nickname;
        this.password = password;
        this.birthYear = birthYear;
        this.gender = gender;
    }

    public void updateUser(String nickname) {this.nickname = nickname;}

    public void changePassword(String password) {this.password = password;}
}
