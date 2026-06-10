package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.character.dto.CreateCharacterRequest;
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
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        ChatCharacter chatCharacter = ChatCharacter.builder()
                .user(user)
                .name(request.name())
                .description(request.description())
                .persona(request.persona())
                .build();
        return characterRepository.save(chatCharacter);
    }

    @Transactional(readOnly = true)
    public CharacterResponse getCharacter(Long id) {
        ChatCharacter chatCharacter = characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("캐릭터 없음"));
        return CharacterResponse.from(chatCharacter);
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> getMyCharacters(String loginId) {
        return characterRepository.findByUser_LoginId(loginId).stream()
                .map(CharacterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CharacterResponse> getCharacters(Pageable pageable) {
        return characterRepository.findAll(pageable)
                .map(CharacterResponse::from);
    }

    private ChatCharacter getOwnedCharacter(String loginId, Long characterId) {
        ChatCharacter character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("캐릭터 없음"));
        if (!character.getUser().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("본인 캐릭터 아님");
        }
        return character;
    }
}
