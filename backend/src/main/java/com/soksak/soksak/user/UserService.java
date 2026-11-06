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

import java.time.Year;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /**
     * 가입 보너스. 메시지 1건에 1마디라 100턴이고, 3마디짜리 모델만 써도 30턴은 된다.
     *
     * 결제는 만들지 않기로 해서 자동 지급은 이것 하나뿐이다.
     * 충전 API, 쿠폰, 구독은 만들지 않는다. 추가 지급은 관리자가 SQL로 넣는다(MANUAL_GRANT).
     */
    private static final int SIGNUP_BONUS = 100;

    // 화면의 출생연도 목록이 여기서 시작한다. 이보다 이른 값은 직접 만든 요청뿐이다.
    private static final Year MIN_BIRTH_YEAR = Year.of(1900);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPersonaService userPersonaService;
    private final CreditService creditService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // unique 제약에만 맡기면 어떤 값이 겹쳤는지 모르는 409가 되므로 저장 전에 하나씩 확인한다.
        // 동시에 같은 값으로 가입하면 둘 다 여기를 통과할 수 있는데, 그때는 unique 제약이 막는다.
        if (request.birthYear().isBefore(MIN_BIRTH_YEAR)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(request.email())
                .loginId(request.loginId())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .birthYear(request.birthYear().getValue())
                .gender(request.gender())
                .build();
        User saved = userRepository.save(user);

        // 가입 보너스도 같은 트랜잭션에서. 잔액 컬럼을 직접 100으로 만들지 않고 원장을 거치는
        // 이유는 불변식(SUM(delta) == credit_balance)이 첫 줄부터 성립하게 하기 위해서다.
        creditService.grant(saved, SIGNUP_BONUS, CreditReason.SIGNUP_BONUS, "가입 보너스");

        // 가입정보로 기본 유저 페르소나를 함께 생성한다(같은 트랜잭션).
        // 페르소나 이름의 씨앗은 표시용 닉네임을 쓴다(본명은 수집하지 않음).
        userPersonaService.createDefault(
                saved.getId(), saved.getNickname(),
                Year.now().getValue() - saved.getBirthYear(), saved.getGender());
        return UserResponse.from(saved);
    }

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = findUser(userId);

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
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        // 위를 통과했으면 currentPassword가 곧 지금 비밀번호의 평문이라 평문끼리 비교하면 된다.
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.deleteByUser_Id(user.getId());

    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
