package com.soksak.soksak.chatRoom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
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

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRoomCrudE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String OWNER = "owner";
    private static final String OTHER = "other";
    private static final String PASSWORD = "pw123456";

    private String ownerToken;
    private String otherToken;
    private long ownerCharacterId;   // OWNER 소유 캐릭터
    private long otherCharacterId;   // OTHER 소유 캐릭터

    @BeforeEach
    void setUp() throws Exception {
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        User owner = seedUser(OWNER, "주인장", "owner@soksak.com");
        User other = seedUser(OTHER, "타인", "other@soksak.com");

        ownerToken = accessToken(OWNER);
        otherToken = accessToken(OTHER);

        ownerCharacterId = seedCharacter(owner, "릴리");
        otherCharacterId = seedCharacter(other, "남의캐릭");
    }

    // ---------- CREATE ----------

    @Test
    @DisplayName("인증된 유저가 챗룸을 생성하면 201과 본문을 반환하고 제목은 캐릭터 이름이 된다")
    void create_returns_201_with_body() throws Exception {
        mockMvc.perform(post("/chatrooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("characterId", ownerCharacterId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("릴리"))
                .andExpect(jsonPath("$.characterId").value((int) ownerCharacterId));
    }

    @Test
    @DisplayName("characterId가 없으면 생성은 400을 반환한다")
    void create_without_characterId_returns_400() throws Exception {
        mockMvc.perform(post("/chatrooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Collections.emptyMap())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이 생성하면 403을 반환한다")
    void create_without_token_returns_403() throws Exception {
        mockMvc.perform(post("/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("characterId", ownerCharacterId))))
                .andExpect(status().isForbidden());
    }

    // ---------- READ ----------

    @Test
    @DisplayName("본인 챗룸 단건 조회는 200과 챗룸을 반환한다")
    void get_one_returns_200() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(get("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.title").value("릴리"));
    }

    @Test
    @DisplayName("내 챗룸 목록은 본인 챗룸만 반환한다")
    void get_my_chatrooms_returns_only_mine() throws Exception {
        createChatRoom(ownerToken, ownerCharacterId);
        createChatRoom(ownerToken, ownerCharacterId);
        createChatRoom(otherToken, otherCharacterId);

        mockMvc.perform(get("/chatrooms")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("남의 챗룸 단건 조회는 차단된다")
    void get_others_chatroom_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(get("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // ---------- UPDATE ----------

    @Test
    @DisplayName("본인 챗룸 제목 수정은 200이고 변경이 DB에 반영된다")
    void update_own_chatroom_persists() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(patch("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "바뀐제목"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐제목"));

        // dirty checking으로 실제 반영됐는지 DB에서 확인
        assertThat(chatRoomRepository.findById(id).orElseThrow().getTitle()).isEqualTo("바뀐제목");
    }

    @Test
    @DisplayName("제목이 빈 값이면 수정은 400을 반환한다")
    void update_with_blank_title_returns_400() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(patch("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 챗룸 수정은 차단되고 제목이 바뀌지 않는다")
    void update_others_chatroom_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(patch("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "해킹"))))
                .andExpect(status().isForbidden());

        // 인가 핵심: 차단됐으니 제목은 그대로여야 한다 (상태코드와 무관하게 안정적인 검증)
        assertThat(chatRoomRepository.findById(id).orElseThrow().getTitle()).isEqualTo("릴리");
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("본인 챗룸 삭제는 204이고 실제로 삭제된다")
    void delete_own_chatroom_removes_it() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(delete("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(chatRoomRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("남의 챗룸 삭제는 차단되고 챗룸이 유지된다")
    void delete_others_chatroom_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(delete("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(chatRoomRepository.findById(id)).isPresent();
    }

    // ---------- helpers ----------

    private User seedUser(String loginId, String nickname, String email) {
        return userRepository.save(User.builder()
                .loginId(loginId)
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(PASSWORD))
                .name(nickname)
                .age(20)
                .gender(com.soksak.soksak.common.Gender.MALE)
                .build());
    }

    private long seedCharacter(User user, String name) {
        return characterRepository.save(ChatCharacter.builder()
                .user(user)
                .name(name)
                .description("설명")
                .persona("페르소나")
                .build()).getId();
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

    private long createChatRoom(String token, long characterId) throws Exception {
        MvcResult result = mockMvc.perform(post("/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("characterId", characterId))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}