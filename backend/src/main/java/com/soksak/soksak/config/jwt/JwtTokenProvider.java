package com.soksak.soksak.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    private SecretKey key;
    public static final String AUTHORITIES_KEY = "auth"; //

    //
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecretKey()));
    }

    // accessToken 생성
    public String generateToken(Authentication authentication, Long validTime) {
        // 권한 목록을 문자열로
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();
        Date expiration = new Date(now.getTime() + validTime);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(AUTHORITIES_KEY, authorities )
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getRefreshTokenExpiration());
    }

    public boolean validToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key) // 비밀키로 서명 검증
                    .build()
                    .parseSignedClaims(token); // 서명 만료 형식 검사
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false; // 만료 위조 형식 오류
        }
    }

    public Authentication getAuthentication(String token) {
        // 토큰에서 clamis 꺼내기
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // "auth" claim을 다시 권한 목록으로 복원
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), "", authorities);
    }
}
