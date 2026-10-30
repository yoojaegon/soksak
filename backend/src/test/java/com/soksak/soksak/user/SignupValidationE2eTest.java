package com.soksak.soksak.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 검증 실패 응답의 errors 형식. 프론트가 칸마다 문구를 띄우는 데 기대는 계약이다.
// 모두 400에서 끝나서 DB에 남는 게 없으므로 정리하지 않는다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupValidationE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("비밀번호가 비어 있으면 길이·형식 에러 없이 필수 에러 하나만 보낸다")
    void blank_password_returns_only_required_error() throws Exception {
        mockMvc.perform(signup(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].reason").value("비밀번호는 필수입니다."));
    }

    @Test
    @DisplayName("한 필드에 에러가 여러 개면 길이 에러가 형식 에러보다 먼저 온다")
    void multiple_errors_on_one_field_are_ordered() throws Exception {
        mockMvc.perform(signup("abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].reason").value("비밀번호는 8~64자여야 합니다."))
                .andExpect(jsonPath("$.errors[1].reason").value("비밀번호는 영문과 숫자를 각각 하나 이상 포함해야 합니다."));
    }

    private RequestBuilder signup(String password) throws Exception {
        return post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "v@soksak.com", "loginId", "validator", "nickname", "검증",
                        "password", password, "age", 20, "gender", "MALE")));
    }
}
