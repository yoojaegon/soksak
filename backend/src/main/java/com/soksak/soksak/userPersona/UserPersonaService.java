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
    public UserPersonaResponse create(Long userId, CreateUserPersonaRequest request) {
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
    public UserPersonaResponse update(Long userId, Long id, UpdateUserPersonaRequest request) {
        UserPersona persona = getOwnedPersona(userId, id);
        persona.update(request.name(), request.persona());
        return UserPersonaResponse.from(persona);
    }

    /** 지정한 페르소나를 기본값으로 바꾼다. (기존 기본값은 해제) */
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
     * - 마지막 한 개는 삭제할 수 없다. (대화에 쓸 페르소나가 항상 하나는 남아야 함)
     * - 기본 페르소나를 삭제하면 순서상 바로 앞(없으면 그다음) 페르소나를 기본으로 승격한다.
     */
    @Transactional
    public void delete(Long userId, Long id) {
        // 락을 먼저 잡아야 아래 개수 확인과 삭제 사이에 다른 삭제가 끼어들지 않는다.
        lockUser(userId);
        List<UserPersona> personas = userPersonaRepository.findByUser_IdOrderByIdAsc(userId);
        UserPersona target = findOwned(personas, userId, id);
        if (personas.size() <= 1) {
            throw new BusinessException(ErrorCode.USER_PERSONA_LAST_ONE);
        }

        boolean wasDefault = target.isDefault();
        int idx = personas.indexOf(target);
        userPersonaRepository.delete(target);
        // Hibernate는 DELETE를 맨 마지막에 내보낸다. 먼저 지워 두지 않으면 승격한 기본과
        // 지울 기본이 잠깐 같이 남아 부분 유니크 인덱스에 걸린다.
        userPersonaRepository.flush();

        if (wasDefault) {
            // 앞쪽 이웃을 우선, 맨 앞이었다면 다음 이웃을 기본으로 올린다.
            UserPersona promote = idx > 0 ? personas.get(idx - 1) : personas.get(idx + 1);
            promote.updateDefault(true);
        }
    }

    /** 이미 불러온 목록에서 본인 소유 + 해당 id 페르소나를 찾는다. */
    private UserPersona findOwned(List<UserPersona> personas, Long userId, Long id) {
        return personas.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseGet(() -> getOwnedPersona(userId, id)); // 목록에 없으면 소유/존재 검증으로 적절한 예외
    }

    private UserPersona getOwnedPersona(Long userId, Long id) {
        UserPersona persona = userPersonaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PERSONA_NOT_FOUND));
        if (!persona.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_PERSONA_FORBIDDEN);
        }
        return persona;
    }

    // 기본값을 바꾸거나 개수를 확인하는 작업은 모두 이 락을 먼저 잡는다.
    // 같은 유저의 요청이 겹쳐도 기본은 항상 하나, 페르소나는 최소 하나로 유지된다.
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // 기본은 DB 부분 유니크 인덱스로도 하나만 허용된다. Hibernate는 INSERT를 바로 내보내고
    // UPDATE는 불러온 순서대로 모아서 내보내므로, 여기서 바로 flush하지 않으면
    // 새 기본이 먼저 반영되어 잠깐 기본이 둘이 되고 인덱스에 걸린다.
    private void clearDefault(Long userId) {
        userPersonaRepository.findByUser_IdAndIsDefaultTrue(userId)
                .ifPresent(prev -> {
                    prev.updateDefault(false);
                    userPersonaRepository.flush();
                });
    }

    private UserPersonaResponse save(User user, String name, String persona, boolean requestedDefault) {
        // 첫 페르소나는 요청과 상관없이 기본이다. 대화에 쓸 기본 페르소나가 항상 하나는 있어야 한다.
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
