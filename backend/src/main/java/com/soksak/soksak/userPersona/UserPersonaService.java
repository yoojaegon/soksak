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

    /** 로그인한 유저가 직접 페르소나를 추가한다. 요청하면 바로 기본으로 지정한다. */
    @Transactional
    public UserPersonaResponse createPersona(Long userId, CreateUserPersonaRequest request) {
        User user = lockUser(userId);
        return save(user, request.name(), request.persona(), Boolean.TRUE.equals(request.isDefault()));
    }

    /** 회원가입 시 가입정보(이름/나이/성별)로 기본 페르소나를 생성한다. */
    @Transactional
    public void createDefault(Long userId, String name, int age, Gender gender) {
        User user = lockUser(userId);
        save(user, name, generateDefaultPersona(name, age, gender), false);
    }

    /** 로그인한 본인이 가진 페르소나 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<UserPersonaResponse> getMyPersonas(Long userId) {
        return userPersonaRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(UserPersonaResponse::from)
                .toList();
    }

    /** 본인 페르소나의 내용을 수정한다. */
    @Transactional
    public UserPersonaResponse updatePersona(Long userId, Long id, UpdateUserPersonaRequest request) {
        UserPersona persona = getOwnedPersona(userId, id);
        persona.update(request.name(), request.persona());
        return UserPersonaResponse.from(persona);
    }

    /** 지정한 페르소나를 기본으로 바꾸고, 기존 기본은 해제한다. */
    @Transactional
    public UserPersonaResponse setDefault(Long userId, Long id) {
        lockUser(userId);
        UserPersona persona = getOwnedPersona(userId, id);
        if (!persona.isDefault()) {
            clearDefault(userId);
            persona.updateDefault(true);
        }
        return UserPersonaResponse.from(persona);
    }

    /**
     * 본인 페르소나를 삭제한다.
     * 대화에 쓸 페르소나가 항상 하나는 남아야 하므로 마지막 한 개는 삭제할 수 없다.
     * 기본 페르소나를 삭제하면 바로 앞 페르소나를, 맨 앞이었다면 다음 페르소나를 기본으로 올린다.
     */
    @Transactional
    public void deletePersona(Long userId, Long id) {
        // 락을 먼저 잡아야 아래 개수 확인과 삭제 사이에 다른 삭제가 끼어들지 않는다.
        lockUser(userId);
        UserPersona target = getOwnedPersona(userId, id);
        List<UserPersona> personas = userPersonaRepository.findByUser_IdOrderByIdAsc(userId);
        if (personas.size() <= 1) {
            throw new BusinessException(ErrorCode.USER_PERSONA_LAST_ONE);
        }

        boolean wasDefault = target.isDefault();
        // 같은 트랜잭션에서는 같은 id가 같은 객체로 돌아오므로 목록에서 바로 위치를 찾을 수 있다.
        int idx = personas.indexOf(target);
        userPersonaRepository.delete(target);
        // Hibernate는 DELETE를 가장 나중에 내보내므로 여기서 먼저 flush한다.
        // 기본은 부분 유니크 인덱스로 하나만 허용되어, 지운 기본이 내려간 뒤에 새 기본을 올려야 한다.
        userPersonaRepository.flush();

        if (wasDefault) {
            UserPersona promote = idx > 0 ? personas.get(idx - 1) : personas.get(idx + 1);
            promote.updateDefault(true);
        }
    }

    /** 본인 소유인지 확인하고 페르소나를 반환한다. */
    private UserPersona getOwnedPersona(Long userId, Long id) {
        UserPersona persona = userPersonaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PERSONA_NOT_FOUND));
        if (!persona.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_PERSONA_FORBIDDEN);
        }
        return persona;
    }

    /**
     * 유저 행을 잠가 같은 유저의 페르소나 작업을 한 번에 하나씩 처리한다.
     * 기본을 바꾸거나 개수를 확인하는 작업은 모두 이 락을 먼저 잡아서,
     * 요청이 겹쳐도 기본은 항상 하나, 페르소나는 최소 하나로 유지된다.
     */
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 이 유저의 현재 기본 페르소나를 해제한다.
     * 기본은 부분 유니크 인덱스로 하나만 허용되므로, 해제를 바로 flush해서
     * 새 기본이 DB에 반영되기 전에 기존 기본이 먼저 내려가게 한다.
     */
    private void clearDefault(Long userId) {
        userPersonaRepository.findByUser_IdAndIsDefaultTrue(userId)
                .ifPresent(prev -> {
                    prev.updateDefault(false);
                    userPersonaRepository.flush();
                });
    }

    /** 페르소나를 저장한다. 첫 페르소나이거나 기본 지정을 요청했으면 기본으로 만든다. */
    private UserPersonaResponse save(User user, String name, String persona, boolean requestedDefault) {
        // 대화에 쓸 기본 페르소나가 항상 하나는 있어야 하므로, 첫 페르소나는 요청과 상관없이 기본이다.
        boolean first = !userPersonaRepository.existsByUser_Id(user.getId());
        if (!first && requestedDefault) {
            clearDefault(user.getId());
        }
        UserPersona userPersona = UserPersona.builder()
                .user(user)
                .name(name)
                .persona(persona)
                .isDefault(first || requestedDefault)
                .build();
        return UserPersonaResponse.from(userPersonaRepository.save(userPersona));
    }

    private String generateDefaultPersona(String name, int age, Gender gender) {
        return switch (gender) {
            case MALE -> String.format("내 이름은 %s, %d세 남성이다.", name, age);
            case FEMALE -> String.format("내 이름은 %s, %d세 여성이다.", name, age);
        };
    }
}
