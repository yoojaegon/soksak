package com.soksak.soksak.user;

import com.soksak.soksak.credit.CreditReason;
import com.soksak.soksak.credit.CreditService;
import com.soksak.soksak.user.dto.CreateUserRequest;
import com.soksak.soksak.userPersona.UserPersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /**
     * 가입 보너스. 메시지 1건=1마디니 100턴이고, 비싼 모델(3마디)로만 써도 30턴은 된다.
     * <p>
     * ⛔ 결제는 만들지 않기로 했으므로 자동 지급은 이것 하나뿐이다. 충전 API·쿠폰·구독 주기는
     * 만들지 말 것 — 추가 지급이 필요하면 관리자가 SQL로 넣는다(MANUAL_GRANT).
     */
    private static final int SIGNUP_BONUS = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPersonaService userPersonaService;
    private final CreditService creditService;

    @Transactional
    public User createUser(CreateUserRequest request) {
        User user = User.builder()
                .email(request.email())
                .loginId(request.loginId())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .age(request.age())
                .gender(request.gender())
                .build();
        User saved = userRepository.save(user);

        // 가입 보너스도 같은 트랜잭션에서. 잔액 컬럼을 직접 100으로 만들지 않고 원장을 거치는
        // 이유는 불변식(SUM(delta) == credit_balance)이 첫 줄부터 성립하게 하기 위해서다.
        creditService.grant(saved, SIGNUP_BONUS, CreditReason.SIGNUP_BONUS, "가입 보너스");

        // 가입정보로 기본 유저 페르소나를 함께 생성한다(같은 트랜잭션).
        // 페르소나 이름의 씨앗은 표시용 닉네임을 쓴다(본명은 수집하지 않음).
        userPersonaService.createDefault(
                saved.getLoginId(), saved.getNickname(), saved.getAge(), saved.getGender());
        return saved;
    }
}
