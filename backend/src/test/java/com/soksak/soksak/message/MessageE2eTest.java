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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private User owner;
    private ChatCharacter character;

    @BeforeEach
    void setUp() throws Exception {
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        owner = seedUser(OWNER, "주인장", "owner@soksak.com");
        seedUser(OTHER, "타인", "other@soksak.com");

        ownerToken = accessToken(OWNER);
        otherToken = accessToken(OTHER);

        character = seedCharacter(owner, "릴리");
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

    // ---------- UPDATE ----------

    @Test
    @DisplayName("메시지를 수정하면 content만 교체되고 이후 메시지는 그대로다")
    void update_changes_content_in_place() throws Exception {
        send(ownerToken, "원본 메시지");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        long userMsgId = before.get(0).getId();

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, userMsgId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "수정된 메시지"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) userMsgId))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("수정된 메시지"));

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        assertThat(after).hasSize(2);                                  // 개수 그대로
        assertThat(after.get(0).getContent()).isEqualTo("수정된 메시지");
        assertThat(after.get(1).getId()).isEqualTo(before.get(1).getId()); // 이후 assistant 그대로
    }

    @Test
    @DisplayName("assistant 메시지도 수정할 수 있다 (role 불문)")
    void update_works_on_assistant_message() throws Exception {
        send(ownerToken, "안녕");
        long assistantId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(1).getId();

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, assistantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "사람이 고친 AI 답"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").value("사람이 고친 AI 답"));
    }

    @Test
    @DisplayName("수정 content가 빈 값이면 400을 반환한다")
    void update_with_blank_content_returns_400() throws Exception {
        send(ownerToken, "원본");
        long id = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 방 경로로는 메시지를 수정할 수 없다 (방-메시지 불일치)")
    void update_via_wrong_room_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();
        long otherRoomId = seedChatRoom(owner, character);   // 같은 주인의 다른 방

        // 현재 검증 위반은 임시 IllegalArgumentException. 핸들러가 없어 MockMvc가 예외를 그대로 던진다
        // (실제 컨테이너에선 500). TODO 예외 리팩토링 후 status 검증으로 변경할 것.
        assertThatThrownBy(() -> mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", otherRoomId, msgId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "우회 수정")))))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(messageRepository.findById(msgId)).get()
                .extracting(Message::getContent).isEqualTo("원본");   // 안 바뀜
    }

    @Test
    @DisplayName("남의 방 메시지는 수정할 수 없다")
    void update_others_message_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();

        assertThatThrownBy(() -> mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, msgId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "침입 수정")))))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(messageRepository.findById(msgId)).get()
                .extracting(Message::getContent).isEqualTo("원본");
    }

    @Test
    @DisplayName("토큰 없이 수정하면 403을 반환한다")
    void update_without_token_returns_403() throws Exception {
        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "x"))))
                .andExpect(status().isForbidden());
    }

    // ---------- REGENERATE ----------

    @Test
    @DisplayName("재생성하면 마지막 assistant가 새 응답으로 교체된다 (개수 유지, user 보존)")
    void regenerate_replaces_last_assistant() throws Exception {
        send(ownerToken, "안녕");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        long userId = before.get(0).getId();
        long oldAssistantId = before.get(1).getId();

        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"));

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        assertThat(after).hasSize(2);                                       // user 1 + 새 assistant 1
        assertThat(after.get(0).getId()).isEqualTo(userId);                // user 그대로
        assertThat(after.get(0).getContent()).isEqualTo("안녕");
        assertThat(after.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(after.get(1).getId()).isNotEqualTo(oldAssistantId);     // 새로 생성됨
        assertThat(messageRepository.findById(oldAssistantId)).isEmpty();   // 옛 답은 삭제됨
    }

    @Test
    @DisplayName("대화가 없는 방은 재생성할 수 없다")
    void regenerate_empty_room_throws() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + ownerToken)))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("남의 방은 재생성할 수 없다")
    void regenerate_others_room_is_blocked() throws Exception {
        send(ownerToken, "안녕");

        assertThatThrownBy(() -> mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + otherToken)))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)).hasSize(2); // 그대로
    }

    @Test
    @DisplayName("토큰 없이 재생성하면 403을 반환한다")
    void regenerate_without_token_returns_403() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE (이후 전부) ----------

    @Test
    @DisplayName("특정 메시지부터 이후 메시지가 전부 삭제되고 앞은 남는다")
    void delete_from_removes_target_and_after() throws Exception {
        send(ownerToken, "첫 메시지");
        send(ownerToken, "둘째 메시지");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        assertThat(before).hasSize(4);
        long thirdId = before.get(2).getId();   // 둘째 턴의 user 메시지

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, thirdId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        assertThat(after).hasSize(2);                                  // 첫 턴만 남음
        assertThat(after.get(0).getId()).isEqualTo(before.get(0).getId());
        assertThat(after.get(1).getId()).isEqualTo(before.get(1).getId());
    }

    @Test
    @DisplayName("첫 메시지부터 삭제하면 방이 빈다")
    void delete_from_first_empties_room() throws Exception {
        send(ownerToken, "안녕");
        long firstId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, firstId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)).isEmpty();
    }

    @Test
    @DisplayName("다른 방 경로로는 삭제할 수 없다 (방-메시지 불일치)")
    void delete_via_wrong_room_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();
        long otherRoomId = seedChatRoom(owner, character);   // 같은 주인의 다른 방

        assertThatThrownBy(() -> mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", otherRoomId, msgId)
                        .header("Authorization", "Bearer " + ownerToken)))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)).hasSize(2); // 안 지워짐
    }

    @Test
    @DisplayName("남의 방 메시지는 삭제할 수 없다")
    void delete_others_room_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId).get(0).getId();

        assertThatThrownBy(() -> mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, msgId)
                        .header("Authorization", "Bearer " + otherToken)))
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)).hasSize(2);
    }

    @Test
    @DisplayName("토큰 없이 삭제하면 403을 반환한다")
    void delete_without_token_returns_403() throws Exception {
        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, 1L))
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