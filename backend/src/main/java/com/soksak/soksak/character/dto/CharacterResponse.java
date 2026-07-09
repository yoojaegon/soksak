package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.ChatCharacter;

public record CharacterResponse (
        Long id,
        String characterName,
        String description,
        String persona,
        String greeting,
        Long userId,
        String userName
) {
    public static CharacterResponse from(ChatCharacter character) {
        return new CharacterResponse(
                character.getId(),
                character.getName(),
                character.getDescription(),
                character.getPersona(),
                character.getGreeting(),
                character.getUser().getId(),
                character.getUser().getNickname()
        );
    }
}