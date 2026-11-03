package com.soksak.soksak.config.jwt;

import com.soksak.soksak.auth.AuthUser;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;
    private SecretKey key;
    public static final String AUTHORITIES_KEY = "auth";
    public static final String TOKEN_TYPE_KEY = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    // yml의 비밀키(Base64)를 서명용 키로 한 번만 바꿔 둔다.
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecretKey()));
    }

    // 지금부터 validTime(ms) 동안 유효한 토큰을 만든다.
    private String generateToken(Long userId, Collection<? extends GrantedAuthority> authorities, Long validTime, String type) {
        Date now = new Date();
        return buildToken(userId, authorities, now, new Date(now.getTime() + validTime), type);
    }

    private String buildToken(Long userId, Collection<? extends GrantedAuthority> authorities, Date now, Date expiration, String type) {
        // 권한 목록은 "ROLE_USER,ROLE_ADMIN"처럼 쉼표로 이어 claim 하나에 담는다.
        String authoritiesClaim = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        // subject는 바뀌지도 남에게 다시 주어지지도 않는 userId로 한다.
        // loginId는 바뀌거나 탈퇴 후 다른 사람이 쓸 수 있어서, 옛 토큰이 엉뚱한 사람을 가리킬 수 있다.
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_KEY, type)
                .claim(AUTHORITIES_KEY, authoritiesClaim)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .id(UUID.randomUUID().toString())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
    public String generateAccessToken(Long userId, Collection<? extends GrantedAuthority> authorities) {
        return generateToken(userId, authorities, jwtProperties.getAccessTokenExpiration(), TYPE_ACCESS);
    }

    // 토큰의 exp와 DB에 저장할 만료 시각이 같은 시각에서 나오도록 여기서 함께 계산한다.
    public IssuedToken generateRefreshToken(Long userId, Collection<? extends GrantedAuthority> authorities) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());
        String token = buildToken(userId, authorities, now, expiration, TYPE_REFRESH);
        return new IssuedToken(token, LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()));
    }

    // 서명, 만료, issuer를 검증하고 Claims를 돌려준다. 하나라도 실패하면 empty다.
    // 검증과 파싱을 한 번에 해서, 호출하는 쪽이 같은 토큰을 다시 파싱하지 않아도 된다.
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = parseClaims(token);
            // subject가 userId 숫자가 아니면 거부한다. subject가 loginId였던 예전 토큰이 여기서 걸린다.
            // 실패하면 NumberFormatException이 나는데, IllegalArgumentException의 하위 타입이라 아래 catch로 간다.
            Long.parseLong(claims.getSubject());
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty(); // 만료, 위조, 형식 오류가 모두 여기로 온다
        }
    }

    // 검증한 Claims에서 인증 정보를 복원한다.
    public Authentication getAuthentication(Claims claims) {
        // "auth" claim을 다시 권한 목록으로 되돌린다.
        // claim이 없거나 비어 있으면 split에서 NPE가 나지 않게 권한 없는 인증으로 만든다.
        String authoritiesClaim = claims.get(AUTHORITIES_KEY, String.class);
        Collection<? extends GrantedAuthority> authorities =
                (authoritiesClaim == null || authoritiesClaim.isBlank())
                        ? List.of()
                        : Arrays.stream(authoritiesClaim.split(","))
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        // 인자 3개짜리 생성자여야 인증된 상태로 만들어진다. 2개짜리를 쓰면 인증 안 된 것으로 보고 401이 난다.
        AuthUser principal = new AuthUser(Long.valueOf(claims.getSubject()));
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public String getTokenType(Claims claims) {
        return claims.get(TOKEN_TYPE_KEY, String.class);
    }

    // 서명과 issuer를 검증하고 Claims를 꺼낸다. 실패하면 예외를 던진다.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                // 발급할 때 넣은 issuer와 다르면 거부한다.
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
