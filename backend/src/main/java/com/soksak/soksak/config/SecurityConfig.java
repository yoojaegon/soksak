package com.soksak.soksak.config;

import com.soksak.soksak.config.jwt.JwtFilter;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import com.soksak.soksak.user.CustomUserDetailsService;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    // 비밀번호 해싱과 검증에 쓴다.
    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 로그인할 때 loginId와 비밀번호 검증을 맡긴다(AuthService.login).
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // 모든 요청이 거치는 보안 규칙. 토큰 기반이라 세션·폼 로그인 관련 기능은 전부 끈다.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF는 쿠키·세션 인증을 노리는 공격이라, 헤더로 토큰을 보내는 방식에선 필요 없어서 끈다.
            .csrf(csrf -> csrf.disable())
            // 브라우저 기본 인증 팝업을 끈다.
            .httpBasic(basic -> basic.disable())
            // 폼 로그인은 세션을 만들고 리다이렉트하는 방식이라 끈다. 로그인은 /auth/login API로 한다.
            .formLogin(form -> form.disable())
            // 세션을 만들지 않고 매 요청을 토큰으로만 인증한다(stateless).
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 같은 사이트 안에서만 iframe으로 띄울 수 있게 한다(기본값은 전부 차단).
            .headers(header -> header
                    .frameOptions(frame -> frame.sameOrigin())
            )
            // 인증 안 된 요청은 기본 403 대신 다른 에러와 같은 형식의 401 JSON으로 응답한다.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
            // 경로별 접근 규칙. 위에서부터 순서대로 검사해서 처음 맞는 규칙이 적용된다.
            .authorizeHttpRequests(auth -> auth
                    // SSE 스트리밍이 끝날 때 서버가 같은 요청을 한 번 더 처리하는 단계(ASYNC)는 통과시킨다.
                    // 새 요청이 아니라 처음에 이미 인증을 통과한 요청의 마무리 단계이기 때문이다.
                    // 이 단계에선 JwtFilter가 다시 돌지 않아 인증 정보가 비어 있으므로, 이 규칙이 없으면 거부된다.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/", "/index.html", "/auth/**", "/error").permitAll()
                    // 회원가입(POST /users)만 공개한다. HttpMethod를 빼면 /users의 다른 요청까지 열린다.
                    .requestMatchers(HttpMethod.POST, "/users").permitAll()
                    // 내 캐릭터 목록은 로그인 필요. 아래 /characters/* 규칙에도 걸리는 경로라 반드시 그보다 위에 둔다.
                    .requestMatchers(HttpMethod.GET, "/characters/me").authenticated()
                    // 캐릭터 목록과 상세는 로그인 없이도 볼 수 있다.
                    .requestMatchers(HttpMethod.GET, "/characters", "/characters/*").permitAll()
                    // 업로드된 이미지 보기는 공개한다. 비로그인 상태의 캐릭터 목록에도 이미지가 떠야 해서.
                    // 올리는 건(POST /uploads/images) 아래 anyRequest 규칙에 따라 로그인이 필요하다.
                    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                    // 위에서 안 걸린 나머지는 전부 로그인 필요.
                    .anyRequest().authenticated()
                )
                // 기본 로그인 필터보다 앞에서 JWT를 검사해 인증 정보를 채운다.
                .addFilterBefore(new JwtFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
