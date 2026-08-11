package com.soksak.soksak.userPersona.dto;

import com.soksak.soksak.userPersona.UserPersona;

public record UserPersonaResponse(
        Long id,
        String name,
        String persona,
        boolean isDefault,
        Long userId
) {
    public static UserPersonaResponse from(UserPersona persona) {
        return new UserPersonaResponse(
                persona.getId(),
                persona.getName(),
                persona.getPersona(),
                persona.isDefault(),
                persona.getUser().getId()
        );
    }
}
