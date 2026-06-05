package com.soksak.soksak.auth.dto;

public record TokenResponse (
        String accessToken,
        String refreshToken
){

}
