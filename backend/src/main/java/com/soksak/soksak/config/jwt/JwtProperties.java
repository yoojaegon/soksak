package com.soksak.soksak.config.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("jwt")
public class JwtProperties {
    private final String issuer;
    private final String secretKey;
    private final Long accessTokenExpiration;
    private final Long refreshTokenExpiration;
}
