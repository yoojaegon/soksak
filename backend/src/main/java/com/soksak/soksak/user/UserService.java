package com.soksak.soksak.user;

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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPersonaService userPersonaService;

    @Transactional
    public User createUser(CreateUserRequest request) {
        User user = User.builder()
                .email(request.email())
                .loginId(request.loginId())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .age(request.age())
                .gender(request.gender())
                .build();
        User saved = userRepository.save(user);

        // 가입정보로 기본 유저 페르소나를 함께 생성한다(같은 트랜잭션).
        userPersonaService.createDefault(
                saved.getLoginId(), saved.getName(), saved.getAge(), saved.getGender());
        return saved;
    }
}
