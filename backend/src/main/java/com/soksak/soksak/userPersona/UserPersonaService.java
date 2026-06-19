package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import com.soksak.soksak.userPersona.dto.CreateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UserPersonaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserPersonaService {
    private final UserPersonaRepository userPersonaRepository;
    private final UserRepository userRepository;

    /** 로그인한 유저가 직접 페르소나를 추가한다. */
    @Transactional
    public UserPersonaResponse create(String loginId, CreateUserPersonaRequest request) {
        User user = findUser(loginId);
        return save(user, request.name(), request.gender(), request.age(), request.persona());
    }

    /** 회원가입 시 가입정보(이름/나이/성별)로 기본 페르소나를 생성한다. */
    @Transactional
    public UserPersonaResponse createDefault(String loginId, String name, int age, Gender gender) {
        User user = findUser(loginId);
        return save(user, name, gender, age, generateDefaultPersona(name, age, gender));
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserPersonaResponse save(User user, String name, Gender gender, int age, String persona) {
        UserPersona userPersona = UserPersona.builder()
                .user(user)
                .name(name)
                .gender(gender)
                .age(age)
                .persona(persona)
                // 경로와 무관하게 유저의 첫 페르소나를 기본값으로 둔다.
                .isDefault(!userPersonaRepository.existsByUser(user))
                .build();
        return UserPersonaResponse.from(userPersonaRepository.save(userPersona));
    }

    private String generateDefaultPersona(String name, int age, Gender gender) {
        return switch (gender) {
            case MALE -> String.format("내 이름은 %s, %d세 남성이다.", name, age);
            case FEMALE -> String.format("내 이름은 %s, %d세 여성이다.", name, age);
            case OTHER -> String.format("내 이름은 %s, %d세이다.", name, age);
        };
    }
}
