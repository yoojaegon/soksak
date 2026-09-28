package com.soksak.soksak.user;

import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.credit.CreditReason;
import com.soksak.soksak.credit.CreditService;
import com.soksak.soksak.user.dto.ChangePasswordRequest;
import com.soksak.soksak.user.dto.CreateUserRequest;
import com.soksak.soksak.user.dto.UpdateUserRequest;
import com.soksak.soksak.user.dto.UserResponse;
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
    private final RefreshTokenRepository refreshTokenRepository;

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

    public UserResponse getMe(String loginId) {
        return UserResponse.from(findUser(loginId));
    }

    @Transactional
    public UserResponse updateUser(String loginId, UpdateUserRequest request) {
        User user = findUser(loginId);

        // unique 제약에만 맡기면 커밋 시점에 뭉뚱그린 409("이미 사용 중인 값")가 된다.
        // 자기 닉네임을 그대로 다시 보내는 경우는 통과해야 하므로 본인은 제외하고 본다.
        if (userRepository.existsByNicknameAndIdNot(request.nickname(), user.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 기본 페르소나는 가입 때 닉네임으로 만든 독립 데이터라 여기서 따라 바꾸지 않는다.
        user.updateUser(request.nickname());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(String loginId, ChangePasswordRequest request) {
        User user = findUser(loginId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        // 위를 통과했으면 currentPassword가 곧 지금 비밀번호의 평문이라 평문끼리 비교하면 된다.
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.deleteByUserId(user.getId());

    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
