package com.soksak.soksak.message.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest (
        @NotBlank String content
){
}
