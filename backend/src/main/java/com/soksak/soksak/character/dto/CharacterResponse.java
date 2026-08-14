package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.ChatCharacter;

import java.util.List;

public record CharacterResponse (
        Long id,
        String characterName,
        String description,
        String persona,
        String greeting,
        String imageUrl,
        int likeCount,
        int chatCount,
        List<String> tags,
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
                character.getImageUrl(),
                character.getLikeCount(),
                character.getChatCount(),
                character.getTags().stream().sorted().map(Enum::name).toList(),
                character.getUser().getId(),
                character.getUser().getNickname()
        );
    }
}