package com.soksak.soksak.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthReissueE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String LOGIN_ID = "testuser";
    private static final String PASSWORD = "pw123456";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .loginId(LOGIN_ID)
                .email("test@soksak.com")
                .nickname("tester")
                .password(passwordEncoder.encode(PASSWORD))
                .name("tester")
                .age(20)
                .gender(com.soksak.soksak.common.Gender.MALE)
                .build());
    }

    @Test
    @DisplayName("회전된 옛 refresh를 재사용하면 401을 반환한다 (옛 refresh 무력화)")
    void reusing_rotated_refresh_returns_401() throws Exception {
        // 1) 로그인 → R1 발급
        String r1 = login();

        // 2) R1로 재발급 → R2로 회전. jti 덕분에 R1 != R2
        String r2 = reissueOk(r1);
        assertThat(r2).isNotEqualTo(r1);

        // 3) 회전돼서 폐기된 옛 R1을 다시 쓰면 → row를 못 찾아 401
        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", r1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refreshToken이 빈 값이면 400을 반환한다")
    void blank_refresh_returns_400() throws Exception {
        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인은 401을 반환한다")
    void wrong_password_returns_401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", LOGIN_ID, "password", "wrong-pw"))))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", LOGIN_ID, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(result);
    }

    private String reissueOk(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(result);
    }

    private String extractRefreshToken(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("refreshToken").asText();
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}