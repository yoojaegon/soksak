package com.soksak.soksak.userPersona.dto;

import com.soksak.soksak.common.Gender;
import com.soksak.soksak.userPersona.UserPersona;

public record UserPersonaResponse(
        Long id,
        String name,
        Gender gender,
        int age,
        String persona,
        boolean isDefault,
        Long userId
) {
    public static UserPersonaResponse from(UserPersona persona) {
        return new UserPersonaResponse(
                persona.getId(),
                persona.getName(),
                persona.getGender(),
                persona.getAge(),
                persona.getPersona(),
                persona.isDefault(),
                persona.getUser().getId()
        );
    }
}
