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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    private SecretKey key;
    public static final String AUTHORITIES_KEY = "auth"; //
    public static final String TOKEN_TYPE_KEY = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    //
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecretKey()));
    }

    // accessToken 생성
    public String generateToken(Authentication authentication, Long validTime, String type) {
        // 권한 목록을 문자열로
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now = new Date();
        Date expiration = new Date(now.getTime() + validTime);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(TOKEN_TYPE_KEY, type)
                .claim(AUTHORITIES_KEY, authorities )
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .id(UUID.randomUUID().toString())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getAccessTokenExpiration(), TYPE_ACCESS);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getRefreshTokenExpiration(), TYPE_REFRESH);
    }

    // 서명·만료·형식까지 검증하고 Claims를 돌려준다. 실패하면 empty.
    // (검증과 파싱을 한 번에 처리해 요청당 토큰 재파싱을 없앤다.)
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(parseClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty(); // 만료 위조 형식 오류
        }
    }

    // 검증한 Claims에서 인증 정보를 복원한다.
    public Authentication getAuthentication(Claims claims) {
        // "auth" claim을 다시 권한 목록으로 복원
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), "", authorities);
    }

    public String getTokenType(Claims claims) {
        return claims.get(TOKEN_TYPE_KEY, String.class);
    }

    // 토큰에서 claims 꺼내기 (서명 검증 포함, 실패 시 예외)
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
