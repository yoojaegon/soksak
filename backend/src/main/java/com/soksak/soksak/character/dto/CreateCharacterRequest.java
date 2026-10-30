package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateCharacterRequest (
        // 길이 제한은 ChatCharacter 컬럼 길이와 맞춘다. DB까지 가기 전에 400으로 걸러 낸다.
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
        String name,
        @Size(max = 100, message = "소개는 100자 이하여야 합니다.")
        String description,
        @NotBlank(message = "페르소나는 필수입니다.")
        @Size(max = 1000, message = "페르소나는 1000자 이하여야 합니다.")
        String persona,
        @NotBlank(message = "첫 인사말은 필수입니다.")
        @Size(max = 500, message = "첫 인사말은 500자 이하여야 합니다.")
        String greeting,
        @Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
        String imageUrl,
        // @NotEmpty는 크기만 본다. [null]은 원소 검사로 막는다.
        @NotEmpty(message = "장르는 하나 이상 선택해야 합니다.")
        Set<@NotNull(message = "장르 값이 올바르지 않습니다.") Genre> tags
){
}
