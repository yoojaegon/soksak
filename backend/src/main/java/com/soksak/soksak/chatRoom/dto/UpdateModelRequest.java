package com.soksak.soksak.chatRoom.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateModelRequest (
        @NotBlank String model
) {
}
