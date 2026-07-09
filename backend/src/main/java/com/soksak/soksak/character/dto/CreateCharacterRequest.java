package com.soksak.soksak.character.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCharacterRequest (
        @NotBlank String name,
        String description,
        @NotBlank String persona,
        @NotBlank String greeting
){
}
