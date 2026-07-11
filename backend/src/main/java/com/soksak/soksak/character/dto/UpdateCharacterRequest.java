package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.Genre;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record UpdateCharacterRequest (
        @NotBlank String name,
        String description,
        @NotBlank String persona,
        @NotBlank String greeting,
        Set<Genre> tags
){
}
