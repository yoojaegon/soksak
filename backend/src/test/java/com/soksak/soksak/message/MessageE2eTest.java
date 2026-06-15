package com.soksak.soksak.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String OWNER = "owner";
    private static final String OTHER = "other";
    private static final String PASSWORD = "pw123456";

    private String ownerToken;
    private String otherToken;
    private long roomId;   // OWNER 소유 채팅방

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        User owner = seedUser(OWNER, "주인장", "owner@soksak.com");
        seedUser(OTHER, "타인", "other@soksak.com");

        ownerToken = accessToken(OWNER);
        otherToken = accessToken(OTHER);

        ChatCharacter character = seedCharacter(owner, "릴리");
        roomId = seedChatRoom(owner, character);
    }

    // ---------- SEND ----------

    @Test
    @DisplayName("메시지를 전송하면 201과 AI(assistant) 응답을 반환한다")
    void send_returns_201_with_assistant_reply() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    @DisplayName("전송하면 user 메시지와 assistant 메시지가 순서대로 저장된다")
    void send_persists_user_then_assistant() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isCreated());

        List<Message> saved = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(saved.get(0).getContent()).isEqualTo("안녕 릴리야");
        assertThat(saved.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        // assistant 응답은 유저 입력을 그대로 따라하지 않는다 (에코 버그 회귀 방지)
        assertThat(saved.get(1).getContent()).isNotEqualTo("안녕 릴리야");
    }

    @Test
    @DisplayName("content가 빈 값이면 전송은 400을 반환한다")
    void send_with_blank_content_returns_400() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이 전송하면 403을 반환한다")
    void send_without_token_returns_403() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("남의 채팅방에는 메시지를 전송할 수 없다")
    void send_to_others_room_is_blocked() throws Exception {
        // 현재 소유권 위반은 임시 IllegalArgumentException. 핸들러가 없어 MockMvc가 예외를 그대로 던진다
        // (실제 컨테이너에선 500). TODO 예외 리팩토링 후 .andExpect(status().isForbidden())로 변경할 것.
        assertThatThrownBy(() -> mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "침입")))))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        // 차단됐으니 메시지는 하나도 저장되지 않아야 한다
        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)).isEmpty();
    }

    // ---------- READ ----------

    @Test
    @DisplayName("메시지가 없는 방의 조회는 빈 배열을 반환한다")
    void get_empty_room_returns_empty_array() throws Exception {
        mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("대화 내역은 보낸 순서(user→assistant)대로 조회된다")
    void get_returns_messages_in_order() throws Exception {
        send(ownerToken, "첫 메시지");
        send(ownerToken, "둘째 메시지");

        mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("첫 메시지"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[2].role").value("USER"))
                .andExpect(jsonPath("$[2].content").value("둘째 메시지"))
                .andExpect(jsonPath("$[3].role").value("ASSISTANT"));
    }

    @Test
    @DisplayName("남의 채팅방 대화 내역은 조회할 수 없다")
    void get_others_room_is_blocked() throws Exception {
        send(ownerToken, "비밀 대화");

        // 현재 소유권 위반은 임시 IllegalArgumentException. 핸들러가 없어 MockMvc가 예외를 그대로 던진다
        // (실제 컨테이너에선 500). TODO 예외 리팩토링 후 .andExpect(status().isForbidden())로 변경할 것.
        assertThatThrownBy(() -> mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + otherToken)))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("토큰 없이 조회하면 403을 반환한다")
    void get_without_token_returns_403() throws Exception {
        mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private User seedUser(String loginId, String nickname, String email) {
        return userRepository.save(User.builder()
                .loginId(loginId)
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(PASSWORD))
                .build());
    }

    private ChatCharacter seedCharacter(User user, String name) {
        return characterRepository.save(ChatCharacter.builder()
                .user(user)
                .name(name)
                .description("설명")
                .persona("페르소나")
                .build());
    }

    private long seedChatRoom(User user, ChatCharacter character) {
        return chatRoomRepository.save(ChatRoom.builder()
                .user(user)
                .character(character)
                .title(character.getName())
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

    private void send(String token, String content) throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", content))))
                .andExpect(status().isCreated());
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}