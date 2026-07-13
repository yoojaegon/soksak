package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateCharacterRequest (
        @NotBlank String name,
        String description,
        @NotBlank String persona,
        @NotBlank String greeting,
        @NotEmpty Set<Genre> tags
){
}
