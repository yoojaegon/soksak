package com.soksak.soksak.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.TestcontainersConfig;
import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.character.Genre;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.credit.CreditLedgerRepository;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.userPersona.UserPersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사용자 채팅 기본값 수정. 방은 건드리지 않고, 상속하는 방만 새 기본값을 따른다.
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserChatDefaultsE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ChatSummaryRepository chatSummaryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CreditLedgerRepository creditLedgerRepository;
    @Autowired UserPersonaRepository userPersonaRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String LOGIN_ID = "picker";
    private static final String PASSWORD = "pw123456";
    private static final String OPUS = "anthropic/claude-opus-4.8";
    private static final String HAIKU = "anthropic/claude-haiku-4.5";

    private String token;
    private User user;
    private ChatCharacter character;

    @BeforeEach
    void setUp() throws Exception {
        chatSummaryRepository.deleteAll();
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        userPersonaRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        creditLedgerRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .loginId(LOGIN_ID).email("picker@soksak.com").nickname("고르는사람")
                .password(passwordEncoder.encode(PASSWORD)).birthYear(2000).gender(Gender.FEMALE)
                .build());
        character = characterRepository.save(ChatCharacter.builder()
                .user(user).name("릴리").description("설명").persona("페르소나")
                .greeting("안녕").tags(Set.of(Genre.DAILY))
                .build());
        token = login();
    }

    @Test
    @DisplayName("가입 직후 기본값은 카탈로그 기본 모델과 추론 끔이고 /users/me에 나온다")
    void me_shows_signup_defaults() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value(ModelCatalog.defaultId()))
                .andExpect(jsonPath("$.defaultThinkingLevel").value("off"));
    }

    @Test
    @DisplayName("보낸 필드만 바뀌고 응답에 반영된다")
    void partial_update_changes_only_sent_fields() throws Exception {
        patchDefaults(Map.of("model", OPUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value(OPUS))
                .andExpect(jsonPath("$.defaultThinkingLevel").value("off"));

        patchDefaults(Map.of("thinkingLevel", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value(OPUS))
                .andExpect(jsonPath("$.defaultThinkingLevel").value("high"));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getDefaultModel()).isEqualTo(OPUS);
        assertThat(saved.getDefaultThinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("추론은 모델이 못 받는 값이어도 고른 그대로 저장한다")
    void thinking_is_stored_as_chosen() throws Exception {
        patchDefaults(Map.of("model", HAIKU, "thinkingLevel", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultThinkingLevel").value("high"));
    }

    @Test
    @DisplayName("빈 본문은 400이고 기본값이 바뀌지 않는다")
    void empty_body_returns_400() throws Exception {
        patchDefaults(Collections.emptyMap()).andExpect(status().isBadRequest());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getDefaultModel())
                .isEqualTo(ModelCatalog.defaultId());
    }

    @Test
    @DisplayName("카탈로그에 없는 모델이나 공백은 400이다")
    void unknown_or_blank_model_returns_400() throws Exception {
        patchDefaults(Map.of("model", "openai/nope-9000")).andExpect(status().isBadRequest());
        patchDefaults(Map.of("model", "  ")).andExpect(status().isBadRequest());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getDefaultModel())
                .isEqualTo(ModelCatalog.defaultId());
    }

    @Test
    @DisplayName("없는 추론 레벨은 400이다")
    void unknown_thinking_level_returns_400() throws Exception {
        patchDefaults(Map.of("thinkingLevel", "extreme")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기본값을 바꾸면 상속하는 방만 따라가고 고정한 방은 그대로다")
    void only_inheriting_rooms_follow_the_new_default() throws Exception {
        ChatRoom inheriting = chatRoomRepository.save(ChatRoom.builder()
                .user(user).character(character).title("상속").build());
        ChatRoom fixed = ChatRoom.builder().user(user).character(character).title("고정").build();
        fixed.update(null, HAIKU, null, null, null);
        fixed = chatRoomRepository.save(fixed);

        patchDefaults(Map.of("model", OPUS)).andExpect(status().isOk());

        assertThat(effectiveModelOf(inheriting.getId())).isEqualTo(OPUS);
        assertThat(effectiveModelOf(fixed.getId())).isEqualTo(HAIKU);
        // 방 행은 손대지 않는다
        assertThat(chatRoomRepository.findById(inheriting.getId()).orElseThrow().getModel()).isNull();
    }

    private String effectiveModelOf(Long roomId) {
        return chatRoomRepository.findByIdWithDetails(roomId).orElseThrow().effectiveModel();
    }

    private ResultActions patchDefaults(Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch("/users/me/chat-defaults")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
