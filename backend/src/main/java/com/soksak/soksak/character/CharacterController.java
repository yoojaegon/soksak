package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.character.dto.CreateCharacterRequest;
import com.soksak.soksak.character.dto.UpdateCharacterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CharacterController {
    private final CharacterService characterService;

    @PostMapping("/characters/create")
    public ResponseEntity<CharacterResponse> createCharacter(
            Authentication authentication,
            @Valid @RequestBody CreateCharacterRequest request) {
        ChatCharacter chatCharacter = characterService.createCharacter(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CharacterResponse.from(chatCharacter));
    }

    @GetMapping("/characters/{id}")
    public ResponseEntity<CharacterResponse> getCharacter(@PathVariable Long id) {
        return ResponseEntity.ok(characterService.getCharacter(id));
    }

    @GetMapping("/characters/me")
    public ResponseEntity<List<CharacterResponse>> getMyCharacters(Authentication authentication) {
        return ResponseEntity.ok(characterService.getMyCharacters(authentication.getName()));
    }

    @GetMapping("/characters")
    public ResponseEntity<Page<CharacterResponse>> getCharacters(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(characterService.getCharacters(pageable));
    }

    @PutMapping("/characters/{id}")
    public ResponseEntity<CharacterResponse> updateCharacter(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCharacterRequest request) {
        CharacterResponse response = characterService.updateCharacter(authentication.getName(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/characters/{id}")
    public ResponseEntity<Void> deleteCharacter(
            Authentication authentication,
            @PathVariable Long id) {
        characterService.deleteCharacter(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
