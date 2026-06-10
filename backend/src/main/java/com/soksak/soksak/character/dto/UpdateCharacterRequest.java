package com.soksak.soksak.character.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCharacterRequest (
        @NotBlank String name,
        String description,
        @NotBlank String persona
){
}
