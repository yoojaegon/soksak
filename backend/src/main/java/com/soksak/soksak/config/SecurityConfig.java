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
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    // 비밀번호 해싱, 검증용
    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //로그인 시 loginId + 비번 검증을 위임
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // csrf는 폼 / 세션기반 방어 불필요해서 끔
            .csrf(csrf -> csrf.disable())
            // 브라우저 기본 인증 팝업 끔
            .httpBasic(basic -> basic.disable())
            // 폼 로그인은 세션을 만들고 브라우저가 리다이렉트 해주는것 세션을 안씀
            .formLogin(form -> form.disable())
            // 세션은 안만들고 토큰으로만 인증(stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .headers(header -> header
                    .frameOptions(frame -> frame.sameOrigin())
            )

            // 미인증 요청은 기본 403 대신 앱 공통 형태의 401 JSON으로 응답
            .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))

            .authorizeHttpRequests(auth -> auth
                    // SSE가 끝나면 컨테이너가 같은 요청을 ASYNC로 한 번 더 디스패치한다. 그때 이 체인이
                    // 다시 도는데, JwtFilter(OncePerRequestFilter)는 비동기 디스패치엔 안 끼므로
                    // SecurityContext가 비어 있다 → 이미 통과한 요청이 여기서 Access Denied가 된다.
                    // 응답은 이미 커밋된 뒤라 에러 페이지도 못 그려서, AI 실패 1건마다 무관한 ERROR
                    // 스택이 셋씩 찍혔다(원인인 ChatAiServerClient WARN 한 줄이 파묻히던 이유).
                    // ASYNC는 새 요청이 아니라 이미 인가된 요청의 연장이므로 통과시키는 게 맞다.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/", "/index.html", "/auth/**", "/error").permitAll()
                    // 회원가입(POST /users)만 공개. 메서드를 빼면 /users의 다른 요청까지 열린다.
                    .requestMatchers(HttpMethod.POST, "/users").permitAll()
                    // 내 캐릭터 목록은 인증 필요 (아래 공개 규칙보다 먼저 매칭되어야 함)
                    .requestMatchers(HttpMethod.GET, "/characters/me").authenticated()
                    // 로그인 없이도 캐릭터 둘러보기(목록/상세) 가능
                    .requestMatchers(HttpMethod.GET, "/characters", "/characters/*").permitAll()
                    // 업로드된 이미지 보기는 공개(비로그인 카탈로그에도 캐릭터 이미지가 뜬다).
                    // 올리는 것(POST /uploads/images)은 아래 anyRequest 규칙대로 인증 필요.
                    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                    .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
