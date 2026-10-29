package com.soksak.soksak.character.dto;

import com.soksak.soksak.character.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateCharacterRequest (
        // 길이는 ChatCharacter 컬럼 길이와 같다. 넘기면 DB에서 터져 400 대신 엉뚱한 에러가 나간다.
        @NotBlank @Size(max = 30) String name,
        @Size(max = 100) String description,
        @NotBlank @Size(max = 1000) String persona,
        @NotBlank @Size(max = 500) String greeting,
        @Size(max = 500) String imageUrl,
        @NotEmpty Set<@NotNull Genre> tags   // @NotEmpty는 크기만 본다. [null]은 원소 검사로 막는다.
){
}
