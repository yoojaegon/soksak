package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import com.soksak.soksak.userPersona.dto.CreateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UpdateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UserPersonaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /** 로그인한 본인이 가진 페르소나 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<UserPersonaResponse> getMyPersonas(String loginId) {
        return userPersonaRepository.findByUser_LoginIdOrderByIdAsc(loginId).stream()
                .map(UserPersonaResponse::from)
                .toList();
    }

    /** 본인 페르소나의 내용을 수정한다. */
    @Transactional
    public UserPersonaResponse update(String loginId, Long id, UpdateUserPersonaRequest request) {
        UserPersona persona = getOwnedPersona(loginId, id);
        persona.update(request.name(), request.gender(), request.age(), request.persona());
        return UserPersonaResponse.from(persona);
    }

    /** 지정한 페르소나를 기본값으로 바꾼다. (기존 기본값은 해제) */
    @Transactional
    public UserPersonaResponse setDefault(String loginId, Long id) {
        UserPersona persona = getOwnedPersona(loginId, id);
        if (!persona.isDefault()) {
            userPersonaRepository.findByUserAndIsDefaultTrue(persona.getUser())
                    .ifPresent(prev -> prev.updateDefault(false));
            persona.updateDefault(true);
        }
        return UserPersonaResponse.from(persona);
    }

    /**
     * 본인 페르소나를 삭제한다.
     * - 마지막 한 개는 삭제할 수 없다. (대화에 쓸 페르소나가 항상 하나는 남아야 함)
     * - 기본 페르소나를 삭제하면 순서상 바로 앞(없으면 그다음) 페르소나를 기본으로 승격한다.
     */
    @Transactional
    public void delete(String loginId, Long id) {
        List<UserPersona> personas = userPersonaRepository.findByUser_LoginIdOrderByIdAsc(loginId);
        UserPersona target = findOwned(personas, loginId, id);
        if (personas.size() <= 1) {
            throw new BusinessException(ErrorCode.USER_PERSONA_LAST_ONE);
        }

        boolean wasDefault = target.isDefault();
        int idx = personas.indexOf(target);
        userPersonaRepository.delete(target);

        if (wasDefault) {
            // 앞쪽 이웃을 우선, 맨 앞이었다면 다음 이웃을 기본으로 올린다.
            UserPersona promote = idx > 0 ? personas.get(idx - 1) : personas.get(idx + 1);
            promote.updateDefault(true);
        }
    }

    /** 이미 불러온 목록에서 본인 소유 + 해당 id 페르소나를 찾는다. */
    private UserPersona findOwned(List<UserPersona> personas, String loginId, Long id) {
        return personas.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseGet(() -> getOwnedPersona(loginId, id)); // 목록에 없으면 소유/존재 검증으로 적절한 예외
    }

    private UserPersona getOwnedPersona(String loginId, Long id) {
        UserPersona persona = userPersonaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PERSONA_NOT_FOUND));
        if (!persona.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.USER_PERSONA_FORBIDDEN);
        }
        return persona;
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
