package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CreateCharacterRequest;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
