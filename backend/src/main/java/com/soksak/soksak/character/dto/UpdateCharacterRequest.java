package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateCharacterRequest (
        @NotBlank String name,
        String description,
        @NotBlank String persona,
        @NotBlank String greeting,
        @Size(max = 500) String imageUrl,
        @NotEmpty Set<Genre> tags
){
}
