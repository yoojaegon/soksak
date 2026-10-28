package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.character.dto.CreateCharacterRequest;
import com.soksak.soksak.character.dto.UpdateCharacterRequest;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CharacterService {
    // 화면의 정렬 옵션(최신순·인기순·대화 많은순)과 같은 목록
    private static final Set<String> SORTABLE = Set.of("createdAt", "likeCount", "chatCount");

    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public CharacterResponse createCharacter(String loginId, CreateCharacterRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ChatCharacter chatCharacter = ChatCharacter.builder()
                .user(user)
                .name(request.name())
                .description(request.description())
                .persona(request.persona())
                .greeting(request.greeting())
                .imageUrl(normalizeImageUrl(request.imageUrl()))
                .tags(request.tags())
                .build();
        return CharacterResponse.from(characterRepository.save(chatCharacter));
    }

    @Transactional(readOnly = true)
    public CharacterResponse getCharacter(Long id, String loginId) {
        ChatCharacter chatCharacter = characterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        if (chatCharacter.isHidden() && !canSeeHidden(chatCharacter, loginId)) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);   // 숨겨졌다는 사실도 드러내지 않는다
        }
        return CharacterResponse.from(chatCharacter);
    }

    // 숨겨진 캐릭터는 제작자와 이미 방이 있는 사람만 본다(비로그인이면 loginId가 null).
    private boolean canSeeHidden(ChatCharacter character, String loginId) {
        if (loginId == null) return false;
        return character.getUser().getLoginId().equals(loginId)
                || chatRoomRepository.existsByUser_LoginIdAndCharacter_Id(loginId, character.getId());
    }

    @Transactional(readOnly = true)
    public Page<CharacterResponse> getMyCharacters(String loginId, Pageable pageable) {
        return characterRepository.findByUser_LoginId(loginId, sortablePage(pageable))
                .map(CharacterResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<CharacterResponse> getCharacters(String q, Genre tag, Pageable pageable) {
        String keyword = (q == null || q.isBlank()) ? null : escapeLike(q);
        return characterRepository.search(keyword, tag, sortablePage(pageable))
                .map(CharacterResponse::from);
    }

    // 정렬은 SORTABLE에 있는 필드만 받는다. 나머지는 쿼리에 그대로 붙어 500이 나거나 엉뚱한 필드로 정렬된다.
    // 정렬 값이 같은 캐릭터끼리는 순서가 정해지지 않아 페이지를 넘길 때 겹치거나 빠지므로 id로 한 번 더 정렬한다.
    private static Pageable sortablePage(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        Sort sort = pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    // LIKE 메타문자(%,_)와 이스케이프 문자(\) 자체를 리터럴로 다루도록 '\'로 이스케이프한다.
    // (쿼리의 escape '\'와 짝. \를 먼저 치환해야 뒤에 붙는 \가 다시 이스케이프되지 않는다.)
    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // 이미지는 선택 입력이라 폼에서 미입력이 빈 문자열('')로 올라온다.
    // 저장 전에 null로 통일해 두지 않으면 DB에 ''과 null이 섞여 "이미지 없음" 판정이 경로마다 갈린다.
    private static String normalizeImageUrl(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.strip();
    }

    @Transactional
    public CharacterResponse updateCharacter(String loginId, Long id, UpdateCharacterRequest request) {
        ChatCharacter character = getOwnedCharacter(loginId, id);
        character.update(request.name(), request.description(), request.persona(), request.greeting(),
                normalizeImageUrl(request.imageUrl()), request.tags());
        return CharacterResponse.from(character);
    }

    @Transactional
    public void deleteCharacter(String loginId, Long id) {
        ChatCharacter character = getOwnedCharacter(loginId, id);
        characterRepository.delete(character);
    }
    public ChatCharacter getOwnedCharacter(String loginId, Long characterId) {
        ChatCharacter character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHARACTER_FORBIDDEN);
        }
        return character;
    }
}
