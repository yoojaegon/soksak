package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.character.dto.CreateCharacterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CharacterController {
    private final CharacterService characterService;

    @PostMapping("/character/create")
    public ResponseEntity<CharacterResponse> createCharacter(
            Authentication authentication,
            @Valid @RequestBody CreateCharacterRequest request) {
        ChatCharacter chatCharacter = characterService.createCharacter(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CharacterResponse.from(chatCharacter));
    }
}
