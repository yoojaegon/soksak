package com.soksak.soksak.config.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@RequiredArgsConstructor
@Validated
@ConfigurationProperties("jwt")
public class JwtProperties {
    @NotBlank
    private final String issuer;
    @NotBlank
    private final String secretKey;
    @NotNull
    private final Long accessTokenExpiration;
    @NotNull
    private final Long refreshTokenExpiration;
}
