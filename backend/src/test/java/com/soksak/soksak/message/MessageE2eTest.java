package com.soksak.soksak.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.credit.CreditLedgerRepository;
import com.soksak.soksak.credit.CreditReason;
import com.soksak.soksak.credit.CreditService;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
class MessageE2eTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ChatSummaryRepository chatSummaryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CreditService creditService;
    @Autowired CreditLedgerRepository creditLedgerRepository;

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
        chatSummaryRepository.deleteAll();
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        creditLedgerRepository.deleteAll();   // users보다 먼저 — user_id FK가 걸려 있다
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

        List<Message> saved = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
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
    @DisplayName("토큰 없이 전송하면 401을 반환한다")
    void send_without_token_returns_401() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("남의 채팅방에는 메시지를 전송할 수 없다")
    void send_to_others_room_is_blocked() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "침입"))))
                .andExpect(status().isForbidden());

        // 차단됐으니 메시지는 하나도 저장되지 않아야 한다
        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).isEmpty();
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

        mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰 없이 조회하면 401을 반환한다")
    void get_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/chatrooms/{roomId}/messages", roomId))
                .andExpect(status().isUnauthorized());
    }

    // ---------- UPDATE ----------

    @Test
    @DisplayName("메시지를 수정하면 content만 교체되고 이후 메시지는 그대로다")
    void update_changes_content_in_place() throws Exception {
        send(ownerToken, "원본 메시지");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        long userMsgId = before.get(0).getId();

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, userMsgId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "수정된 메시지"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) userMsgId))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("수정된 메시지"));

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        assertThat(after).hasSize(2);                                  // 개수 그대로
        assertThat(after.get(0).getContent()).isEqualTo("수정된 메시지");
        assertThat(after.get(1).getId()).isEqualTo(before.get(1).getId()); // 이후 assistant 그대로
    }

    @Test
    @DisplayName("assistant 메시지도 수정할 수 있다 (role 불문)")
    void update_works_on_assistant_message() throws Exception {
        send(ownerToken, "안녕");
        long assistantId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(1).getId();

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
        long id = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();

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
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();
        long otherRoomId = seedChatRoom(owner, character);   // 같은 주인의 다른 방

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", otherRoomId, msgId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "우회 수정"))))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findById(msgId)).get()
                .extracting(Message::getContent).isEqualTo("원본");   // 안 바뀜
    }

    @Test
    @DisplayName("남의 방 메시지는 수정할 수 없다")
    void update_others_message_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();

        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, msgId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "침입 수정"))))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findById(msgId)).get()
                .extracting(Message::getContent).isEqualTo("원본");
    }

    @Test
    @DisplayName("토큰 없이 수정하면 401을 반환한다")
    void update_without_token_returns_401() throws Exception {
        mockMvc.perform(put("/chatrooms/{roomId}/messages/{messageId}", roomId, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "x"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- REGENERATE ----------

    @Test
    @DisplayName("재생성하면 마지막 assistant가 새 응답으로 교체된다 (개수 유지, user 보존)")
    void regenerate_replaces_last_assistant() throws Exception {
        send(ownerToken, "안녕");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        long userId = before.get(0).getId();
        long oldAssistantId = before.get(1).getId();

        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"));

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
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
        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("남의 방은 재생성할 수 없다")
    void regenerate_others_room_is_blocked() throws Exception {
        send(ownerToken, "안녕");

        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).hasSize(2); // 그대로
    }

    @Test
    @DisplayName("토큰 없이 재생성하면 401을 반환한다")
    void regenerate_without_token_returns_401() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages/regenerate", roomId))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE (이후 전부) ----------

    @Test
    @DisplayName("특정 메시지부터 이후 메시지가 전부 삭제되고 앞은 남는다")
    void delete_from_removes_target_and_after() throws Exception {
        send(ownerToken, "첫 메시지");
        send(ownerToken, "둘째 메시지");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        assertThat(before).hasSize(4);
        long thirdId = before.get(2).getId();   // 둘째 턴의 user 메시지

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, thirdId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                // 요약을 굴린 적 없는 방이라 정리할 조각도 없다
                .andExpect(jsonPath("$.summaryTrimmed").value(false));

        List<Message> after = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        assertThat(after).hasSize(2);                                  // 첫 턴만 남음
        assertThat(after.get(0).getId()).isEqualTo(before.get(0).getId());
        assertThat(after.get(1).getId()).isEqualTo(before.get(1).getId());
    }

    @Test
    @DisplayName("첫 메시지부터 삭제하면 방이 빈다")
    void delete_from_first_empties_room() throws Exception {
        send(ownerToken, "안녕");
        long firstId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, firstId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                // 요약을 굴린 적 없는 방이라 정리할 조각도 없다
                .andExpect(jsonPath("$.summaryTrimmed").value(false));

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).isEmpty();
    }

    @Test
    @DisplayName("다른 방 경로로는 삭제할 수 없다 (방-메시지 불일치)")
    void delete_via_wrong_room_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();
        long otherRoomId = seedChatRoom(owner, character);   // 같은 주인의 다른 방

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", otherRoomId, msgId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).hasSize(2); // 안 지워짐
    }

    @Test
    @DisplayName("남의 방 메시지는 삭제할 수 없다")
    void delete_others_room_is_blocked() throws Exception {
        send(ownerToken, "원본");
        long msgId = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).get(0).getId();

        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, msgId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).hasSize(2);
    }

    @Test
    @DisplayName("토큰 없이 삭제하면 401을 반환한다")
    void delete_without_token_returns_401() throws Exception {
        mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, 1L))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 요약 적재 (chat_summary 쌓기) ----------
    // 옛 대화는 messages 원문과 chat_summary 조각 두 벌로 존재한다. 요약은 구간마다 조각
    // 하나를 새로 붙이고 앞 조각은 건드리지 않는다 — ai-server가 이제 넘겨준 구간만 요약해
    // 돌려주므로, 받은 결과로 기존 기억을 덮어쓰면 오래된 구간이 조용히 증발한다.

    @Test
    @DisplayName("대기 분량이 차면 요약 조각이 쌓이고, 두 번째 요약이 첫 조각을 덮어쓰지 않는다")
    void summarize_appends_fragments_without_overwriting_earlier_ones() throws Exception {
        // 첫 요약: 이력 30개 → 최근 20개를 뺀 10개가 첫 조각이 된다
        List<Message> seeded = seedRawMessages(30);
        send(ownerToken, "요약을 굴리는 턴");

        // 둘째 요약: 이력을 10개 더 얹어 대기 분량을 다시 채운다
        seeded.addAll(seedRawMessages(10));
        send(ownerToken, "요약을 한 번 더 굴리는 턴");

        List<ChatSummary> fragments = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId);
        assertThat(fragments).hasSize(2);

        // seq는 1부터, 구간은 겹치지도 비지도 않고 이어져야 한다 —
        // 겹치면 같은 대화가 두 번 기억되고, 비면 그 구간이 영영 요약되지 않는다.
        ChatSummary first = fragments.get(0);
        ChatSummary second = fragments.get(1);
        assertThat(first.getSeq()).isEqualTo(1);
        assertThat(second.getSeq()).isEqualTo(2);
        assertThat(first.getFromMessageId()).isEqualTo(seeded.get(0).getId());
        assertThat(first.getToMessageId()).isEqualTo(seeded.get(9).getId());
        assertThat(second.getFromMessageId()).isEqualTo(seeded.get(10).getId());
        assertThat(second.getToMessageId()).isEqualTo(seeded.get(21).getId());

        // 스텁이 importance를 안 주면 엔티티 기본값(3)으로 들어와야 한다. 0으로 떨어지면
        // 조각 선택에서 늘 뒷전으로 밀린다.
        assertThat(first.getImportance()).isEqualTo(ChatSummary.DEFAULT_IMPORTANCE);
        assertThat(first.getEstimatedTokens()).isPositive();

    }

    @Test
    @DisplayName("대기 분량이 모자라면 요약을 굴리지 않는다")
    void summarize_does_not_run_below_batch_threshold() throws Exception {
        // 최근 20개는 원문 그대로 실리므로 요약 대상이 아니고, 남는 5개는 배치 최소치(10)에 못 미친다
        seedRawMessages(25);
        send(ownerToken, "아직은 이른 턴");

        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId)).isEmpty();
    }

    // ---------- DELETE × 요약(장기기억) ----------
    // 삭제는 원문만 지우므로, 이미 요약에 접혀 들어간 구간까지 지우면 그 조각이 사라진 메시지를
    // 가리킨 채 남는다. 그래서 "잘린 지점에 걸치거나 그 뒤에 있는 조각"을 함께 지운다.
    // 블롭 시절과 결정적으로 다른 점: 지운 대화가 기억에 남지 않고, 앞 구간은 온전히 살아남는다.

    @Test
    @DisplayName("요약 구간보다 뒤를 지우면 조각은 그대로 남는다")
    void delete_after_summary_leaves_fragments_untouched() throws Exception {
        send(ownerToken, "첫째");
        send(ownerToken, "둘째");
        send(ownerToken, "셋째");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        assertThat(before).hasSize(6);
        seedSummary("접어둔 옛 이야기", before.get(0).getId(), before.get(1).getId());

        // 조각이 덮는 구간(0~1)보다 뒤인 4번부터 삭제
        deleteFrom(ownerToken, before.get(4).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryTrimmed").value(false));

        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId))
                .singleElement()
                .extracting(ChatSummary::getContent).isEqualTo("접어둔 옛 이야기");
    }

    @Test
    @DisplayName("요약 구간 안쪽을 지우면 그 조각도 함께 지워진다")
    void delete_inside_summary_removes_the_fragment() throws Exception {
        send(ownerToken, "첫째");
        send(ownerToken, "둘째");
        send(ownerToken, "셋째");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        seedSummary("접어둔 옛 이야기", before.get(0).getId(), before.get(3).getId());

        // 조각이 덮는 구간(0~3)의 한가운데인 2번부터 삭제 → 걸친 조각은 통째로 정리한다.
        // 일부만 도려내지 않는 이유: 요약문은 구간 전체를 하나로 압축한 글이라 쪼갤 수 없다.
        deleteFrom(ownerToken, before.get(2).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryTrimmed").value(true));

        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId)).isEmpty();
    }

    @Test
    @DisplayName("여러 조각 중 잘린 지점에 걸친 것부터 지우고 앞 조각은 남긴다")
    void delete_removes_only_fragments_at_or_after_the_cut() throws Exception {
        send(ownerToken, "첫째");
        send(ownerToken, "둘째");
        send(ownerToken, "셋째");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        seedSummary("첫 구간", before.get(0).getId(), before.get(1).getId());
        seedSummary("둘째 구간", before.get(2).getId(), before.get(3).getId());
        seedSummary("셋째 구간", before.get(4).getId(), before.get(5).getId());

        // 3번부터 삭제 → 둘째 구간(2~3)은 걸쳐서, 셋째 구간(4~5)은 뒤에 있어서 지워지고
        // 첫 구간(0~1)은 온전히 살아남는다. 블롭이었다면 여기서 기억 전체를 잃거나 전부 남겼다.
        deleteFrom(ownerToken, before.get(3).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryTrimmed").value(true));

        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId))
                .singleElement()
                .extracting(ChatSummary::getContent).isEqualTo("첫 구간");
    }

    @Test
    @DisplayName("조각의 마지막 메시지를 지우면 그 조각도 지워진다 (경계 포함)")
    void delete_exactly_at_fragment_end_removes_it() throws Exception {
        send(ownerToken, "첫째");
        send(ownerToken, "둘째");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        seedSummary("접어둔 옛 이야기", before.get(0).getId(), before.get(2).getId());

        // 판정이 >= 가 아니라 > 면 여기서 조각이 사라진 메시지를 계속 가리킨다
        deleteFrom(ownerToken, before.get(2).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryTrimmed").value(true));

        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId)).isEmpty();
    }

    @Test
    @DisplayName("전부 지우면 조각도 전부 사라진다")
    void delete_all_removes_every_fragment() throws Exception {
        send(ownerToken, "첫째");
        send(ownerToken, "둘째");
        List<Message> before = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        seedSummary("첫 구간", before.get(0).getId(), before.get(1).getId());
        seedSummary("둘째 구간", before.get(2).getId(), before.get(3).getId());

        deleteFrom(ownerToken, before.get(0).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryTrimmed").value(true));

        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId)).isEmpty();
        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId)).isEmpty();
    }

    // ---------- helpers ----------

    // 엔티티를 직접 심는 시드라 가입 경로(UserService)를 안 타므로 마디가 0으로 태어난다.
    // 실제 가입자는 보너스를 받고 시작하니 여기서도 넉넉히 채워 준다 — 안 채우면 전송 계열
    // 테스트가 전부 402가 된다(마디 자체를 보는 테스트는 CreditServiceTest가 따로 맡는다).
    private static final int SEED_CREDITS = 1000;

    private User seedUser(String loginId, String nickname, String email) {
        User saved = userRepository.save(User.builder()
                .loginId(loginId)
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(PASSWORD))
                .age(20)
                .gender(com.soksak.soksak.common.Gender.MALE)
                .build());
        creditService.grant(saved, SEED_CREDITS, CreditReason.MANUAL_GRANT, "테스트 시드");
        return saved;
    }

    private ChatCharacter seedCharacter(User user, String name) {
        return characterRepository.save(ChatCharacter.builder()
                .user(user)
                .name(name)
                .description("설명")
                .persona("페르소나")
                .greeting("안녕하세요")
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

    private ResultActions deleteFrom(String token, long messageId) throws Exception {
        return mockMvc.perform(delete("/chatrooms/{roomId}/messages/{messageId}/after", roomId, messageId)
                .header("Authorization", "Bearer " + token));
    }

    // 삭제 규칙만 보고 싶을 땐 요약을 실제로 굴릴 필요가 없다. 결과 조각만 직접 심는다.
    // seq는 이미 심어둔 개수에서 이어 붙여, 호출 순서가 곧 구간 순서가 되게 한다.
    // 방의 블롭도 함께 맞춰 둔다 — 전환기에는 그쪽이 프롬프트에 실리는 값이라, 조각만 심으면
    // 삭제가 블롭까지 정리하는지를 검증할 수 없다.
    private void seedSummary(String content, long fromId, long toId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        int seq = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId).size() + 1;
        chatSummaryRepository.save(ChatSummary.builder()
                .chatRoom(room)
                .seq(seq)
                .fromMessageId(fromId)
                .toMessageId(toId)
                .content(content)
                .importance(ChatSummary.DEFAULT_IMPORTANCE)
                .keywords(List.of("씨앗"))
                .estimatedTokens(20)
                .build());
    }

    // 요약은 "최근 20개를 뺀 나머지가 10개 이상"일 때 굴러간다(SummaryPlan의 WINDOW·BATCH).
    // API로 20턴을 주고받으면 느리고 테스트의 의도도 흐려지므로 이력만 직접 심는다.
    // ⚠️ 그 두 상수를 바꾸면 이 헬퍼를 쓰는 요약 적재 테스트의 개수도 같이 조정해야 한다.
    private List<Message> seedRawMessages(int count) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        List<Message> saved = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            saved.add(messageRepository.save(Message.builder()
                    .chatRoom(room)
                    .role(i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT)
                    .content("이력 " + i)
                    .build()));
        }
        return saved;
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}