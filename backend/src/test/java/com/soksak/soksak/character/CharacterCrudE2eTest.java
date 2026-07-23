package com.soksak.soksak.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.auth.RefreshTokenRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacterCrudE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String OWNER = "owner";
    private static final String OTHER = "other";
    private static final String PASSWORD = "pw123456";

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        characterRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        seedUser(OWNER, "주인장", "owner@soksak.com");
        seedUser(OTHER, "타인", "other@soksak.com");

        ownerToken = accessToken(OWNER);
        otherToken = accessToken(OTHER);
    }

    // ---------- CREATE ----------

    @Test
    @DisplayName("인증된 유저가 캐릭터를 생성하면 201과 본문을 반환한다")
    void create_returns_201_with_body() throws Exception {
        mockMvc.perform(post("/characters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "릴리", "description", "상냥한 마법사", "persona", "다정하다",
                                "greeting", "안녕", "tags", Set.of(Genre.FANTASY)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.characterName").value("릴리"))
                .andExpect(jsonPath("$.persona").value("다정하다"))
                .andExpect(jsonPath("$.userName").value("주인장"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("name이 빈 값이면 생성은 400을 반환한다")
    void create_with_blank_name_returns_400() throws Exception {
        mockMvc.perform(post("/characters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "description", "d", "persona", "p",
                                "greeting", "안녕", "tags", Set.of(Genre.FANTASY)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이 생성하면 401을 반환한다")
    void create_without_token_returns_401() throws Exception {
        mockMvc.perform(post("/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "릴리", "description", "d", "persona", "p"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- READ ----------

    @Test
    @DisplayName("단건 조회는 200과 캐릭터를 반환한다")
    void get_one_returns_200() throws Exception {
        long id = createCharacter(ownerToken, "릴리", "d", "p");

        mockMvc.perform(get("/characters/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.characterName").value("릴리"));
    }

    @Test
    @DisplayName("내 목록 조회는 본인 캐릭터만 반환한다")
    void get_my_characters_returns_only_mine() throws Exception {
        createCharacter(ownerToken, "내캐릭1", "d", "p");
        createCharacter(ownerToken, "내캐릭2", "d", "p");
        createCharacter(otherToken, "남의캐릭", "d", "p");

        mockMvc.perform(get("/characters/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].userName", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("주인장"))));
    }

    @Test
    @DisplayName("전체 목록은 페이징되어 반환된다")
    void get_all_characters_is_paged() throws Exception {
        createCharacter(ownerToken, "c1", "d", "p");
        createCharacter(ownerToken, "c2", "d", "p");
        createCharacter(otherToken, "c3", "d", "p");

        mockMvc.perform(get("/characters")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    // ---------- UPDATE ----------

    @Test
    @DisplayName("본인 캐릭터 수정은 200이고 변경이 DB에 반영된다")
    void update_own_character_persists() throws Exception {
        long id = createCharacter(ownerToken, "원래이름", "원래설명", "원래페르소나");

        mockMvc.perform(put("/characters/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "바뀐이름", "description", "바뀐설명", "persona", "바뀐페르소나",
                                "greeting", "안녕", "tags", Set.of(Genre.FANTASY)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterName").value("바뀐이름"));

        // dirty checking으로 실제 반영됐는지 DB에서 확인
        ChatCharacter saved = characterRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("바뀐이름");
        assertThat(saved.getPersona()).isEqualTo("바뀐페르소나");
    }

    @Test
    @DisplayName("남의 캐릭터 수정은 차단되고 값이 바뀌지 않는다")
    void update_others_character_is_blocked() throws Exception {
        long id = createCharacter(ownerToken, "원래이름", "d", "p");

        mockMvc.perform(put("/characters/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "해킹", "description", "x", "persona", "x",
                                "greeting", "안녕", "tags", Set.of(Genre.FANTASY)))))
                .andExpect(status().isForbidden());

        // 인가 핵심: 차단됐으니 값은 그대로여야 한다 (상태코드와 무관하게 안정적인 검증)
        assertThat(characterRepository.findById(id).orElseThrow().getName()).isEqualTo("원래이름");
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("본인 캐릭터 삭제는 204이고 실제로 삭제된다")
    void delete_own_character_removes_it() throws Exception {
        long id = createCharacter(ownerToken, "삭제대상", "d", "p");

        mockMvc.perform(delete("/characters/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(characterRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("남의 캐릭터 삭제는 차단되고 캐릭터가 유지된다")
    void delete_others_character_is_blocked() throws Exception {
        long id = createCharacter(ownerToken, "삭제대상", "d", "p");

        mockMvc.perform(delete("/characters/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(characterRepository.findById(id)).isPresent();
    }

    // ---------- helpers ----------

    private void seedUser(String loginId, String nickname, String email) {
        userRepository.save(User.builder()
                .loginId(loginId)
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(PASSWORD))
                .age(20)
                .gender(com.soksak.soksak.common.Gender.MALE)
                .build());
    }

    private String accessToken(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createCharacter(String token, String name, String description, String persona) throws Exception {
        MvcResult result = mockMvc.perform(post("/characters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", description, "persona", persona,
                                "greeting", "안녕", "tags", Set.of(Genre.FANTASY)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
