package com.soksak.soksak.character;

import com.soksak.soksak.character.characterLike.CharacterLikeRepository;
import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.character.dto.CreateCharacterRequest;
import com.soksak.soksak.character.dto.UpdateCharacterRequest;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.lore.LoreRepository;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatCharacter createCharacter(String loginId, CreateCharacterRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ChatCharacter chatCharacter = ChatCharacter.builder()
                .user(user)
                .name(request.name())
                .description(request.description())
                .persona(request.persona())
                .greeting(request.greeting())
                .tags(request.tags())
                .build();
        return characterRepository.save(chatCharacter);
    }

    @Transactional(readOnly = true)
    public CharacterResponse getCharacter(Long id) {
        ChatCharacter chatCharacter = characterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        return CharacterResponse.from(chatCharacter);
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> getMyCharacters(String loginId) {
        return characterRepository.findByUser_LoginId(loginId).stream()
                .map(CharacterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CharacterResponse> getCharacters(String q, Genre tag, Pageable pageable) {
        String keyword = (q == null || q.isBlank()) ? null : escapeLike(q);
        return characterRepository.search(keyword, tag, pageable)
                .map(CharacterResponse::from);
    }

    // LIKE 메타문자(%,_)와 이스케이프 문자(\) 자체를 리터럴로 다루도록 '\'로 이스케이프한다.
    // (쿼리의 escape '\'와 짝. \를 먼저 치환해야 뒤에 붙는 \가 다시 이스케이프되지 않는다.)
    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional
    public CharacterResponse updateCharacter(String loginId, Long id, UpdateCharacterRequest request) {
        ChatCharacter character = getOwnedCharacter(loginId, id);
        character.update(request.name(), request.description(), request.persona(), request.greeting(), request.tags());
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
