package com.soksak.soksak.config;

import com.soksak.soksak.config.jwt.JwtFilter;
import com.soksak.soksak.config.jwt.JwtTokenProvider;
import com.soksak.soksak.user.CustomUserDetailsService;
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
                    .requestMatchers("/", "/index.html", "/signup", "/auth/**", "/error").permitAll()
                    // 내 캐릭터 목록은 인증 필요 (아래 공개 규칙보다 먼저 매칭되어야 함)
                    .requestMatchers(HttpMethod.GET, "/characters/me").authenticated()
                    // 로그인 없이도 캐릭터 둘러보기(목록/상세) 가능
                    .requestMatchers(HttpMethod.GET, "/characters", "/characters/*").permitAll()
                    .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
