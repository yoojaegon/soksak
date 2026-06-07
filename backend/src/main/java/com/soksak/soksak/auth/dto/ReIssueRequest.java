package com.soksak.soksak.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReIssueRequest (
        @NotBlank String refreshToken
){
}
