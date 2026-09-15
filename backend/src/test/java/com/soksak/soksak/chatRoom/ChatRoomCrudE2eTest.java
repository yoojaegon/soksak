package com.soksak.soksak.chatRoom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.aiClient.ThinkingLevel;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.chatRoom.chatSummary.SummarySelector;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.message.MessageRole;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.List;
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
    @Autowired MessageRepository messageRepository;
    @Autowired ChatSummaryRepository chatSummaryRepository;
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
        chatSummaryRepository.deleteAll();
        messageRepository.deleteAll();
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
    @DisplayName("토큰 없이 생성하면 401을 반환한다")
    void create_without_token_returns_401() throws Exception {
        mockMvc.perform(post("/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("characterId", ownerCharacterId))))
                .andExpect(status().isUnauthorized());
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

    // ---------- UPDATE MODEL ----------
    // room.model이 null인 건 "아직 안 골랐다"는 서버 초기 상태일 뿐이고(resolve()가 기본값으로 폴백),
    // API로는 받지 않는다. 그래서 아래 400 케이스들은 전부 model이 바뀌지 않아야 한다.

    @Test
    @DisplayName("카탈로그에 있는 모델로 변경하면 200이고 DB에 반영된다")
    void update_model_persists() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        String slug = ModelCatalog.entries().get(0).id();

        mockMvc.perform(patch("/chatrooms/{id}/model", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("model", slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value(slug));

        assertThat(chatRoomRepository.findById(id).orElseThrow().getModel()).isEqualTo(slug);
    }

    @Test
    @DisplayName("model 필드가 없으면 400이고 이미 고른 모델이 지워지지 않는다")
    void update_model_without_field_returns_400_and_keeps_model() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        String slug = ModelCatalog.entries().get(0).id();
        patchModel(ownerToken, id, Map.of("model", slug)).andExpect(status().isOk());

        // 빈 본문이 null로 바인딩돼 선택을 조용히 날려버리면 안 된다
        patchModel(ownerToken, id, Collections.emptyMap()).andExpect(status().isBadRequest());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getModel()).isEqualTo(slug);
    }

    @Test
    @DisplayName("model이 빈 값이면 400을 반환한다")
    void update_model_with_blank_returns_400() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchModel(ownerToken, id, Map.of("model", "  ")).andExpect(status().isBadRequest());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getModel()).isNull();
    }

    @Test
    @DisplayName("카탈로그에 없는 슬러그는 400을 반환한다")
    void update_model_with_unknown_slug_returns_400() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchModel(ownerToken, id, Map.of("model", "openai/nope-9000"))
                .andExpect(status().isBadRequest());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getModel()).isNull();
    }

    @Test
    @DisplayName("남의 챗룸 모델 변경은 차단되고 모델이 바뀌지 않는다")
    void update_others_chatroom_model_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchModel(otherToken, id, Map.of("model", ModelCatalog.entries().get(0).id()))
                .andExpect(status().isForbidden());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getModel()).isNull();
    }

    // ---------- THINKING (추론 깊이) ----------

    @Test
    @DisplayName("추론 레벨을 고르면 저장되고 응답에 그대로 나온다")
    void update_thinking_saves_the_level() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchThinking(ownerToken, id, Map.of("thinkingLevel", "high"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thinkingLevel").value("high"));

        assertThat(chatRoomRepository.findById(id).orElseThrow().getThinkingLevel())
                .isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("thinkingLevel 필드가 없으면 400이고 이미 고른 레벨이 지워지지 않는다")
    void update_thinking_without_field_returns_400_and_keeps_level() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        patchThinking(ownerToken, id, Map.of("thinkingLevel", "medium")).andExpect(status().isOk());

        // model과 같은 이유 — 빈 본문 {}가 null로 바인딩돼 선택을 조용히 날리면 안 된다.
        patchThinking(ownerToken, id, Collections.emptyMap()).andExpect(status().isBadRequest());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getThinkingLevel())
                .isEqualTo(ThinkingLevel.MEDIUM);
    }

    @Test
    @DisplayName("모델이 추론을 못 해도 레벨 저장은 막지 않는다 — 보정은 보낼 때 한다")
    void update_thinking_is_not_validated_against_the_model() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        // 추론을 못 하는 모델을 고른 방에 HIGH를 저장한다. 여기서 400을 내면 모델을
        // 바꿀 때마다 방의 설정이 무효가 돼 사용자가 모델을 못 바꾸게 된다.
        patchModel(ownerToken, id, Map.of("model", "anthropic/claude-haiku-4.5"))
                .andExpect(status().isOk());

        patchThinking(ownerToken, id, Map.of("thinkingLevel", "high")).andExpect(status().isOk());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getThinkingLevel())
                .isEqualTo(ThinkingLevel.HIGH);
        // 실제로 보낼 때만 꺼진다(ModelCatalogThinkingTest가 그 보정을 고정한다).
        assertThat(ModelCatalog.resolveThinking("anthropic/claude-haiku-4.5", ThinkingLevel.HIGH))
                .isEqualTo(ThinkingLevel.OFF);
    }

    @Test
    @DisplayName("없는 레벨 값은 400을 반환한다")
    void update_thinking_with_unknown_level_returns_400() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchThinking(ownerToken, id, Map.of("thinkingLevel", "extreme"))
                .andExpect(status().isBadRequest());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getThinkingLevel()).isNull();
    }

    @Test
    @DisplayName("남의 챗룸 추론 설정 변경은 차단된다")
    void update_others_chatroom_thinking_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        patchThinking(otherToken, id, Map.of("thinkingLevel", "high"))
                .andExpect(status().isForbidden());

        assertThat(chatRoomRepository.findById(id).orElseThrow().getThinkingLevel()).isNull();
    }

    // ---------- SUMMARY (장기기억) ----------
    // 모델과 달리 빈 값이 정당한 입력이다("지우기"). 대신 필드 자체가 없는 건 400 —
    // 빈 본문이 null로 바인딩돼 사용자의 기억을 조용히 날리면 안 된다.
    //
    // ⚠️ 전환 중이다. 기억의 저장소는 chat_summary 조각으로 옮겨갔지만 GET/PATCH는 아직
    // chat_room.summary를 읽고 쓴다(프론트 계약은 안 바꾸기로 했다 — 조각을 이어 붙인
    // 문자열 하나를 주고받는다). 아래에서 커서(summarizedUpToId)를 확인하는 단언들은
    // 그 전환이 끝나면 "조각이 몇 개 남았나"로 바뀐다.

    @Test
    @DisplayName("요약이 없는 방의 장기기억 조회는 200이고 summary가 null이다")
    void get_summary_of_new_room_returns_null() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        mockMvc.perform(get("/chatrooms/{id}/summary", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").doesNotExist());
    }

    @Test
    @DisplayName("장기기억 조회는 저장된 요약문을 반환한다")
    void get_summary_returns_stored_text() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "주인공이 왕자임을 밝혔다.", 42L);

        mockMvc.perform(get("/chatrooms/{id}/summary", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("주인공이 왕자임을 밝혔다."));
    }

    @Test
    @DisplayName("장기기억 수정은 200이고 DB에 반영되며 요약 커서는 그대로다")
    void update_summary_persists_and_keeps_cursor() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "옛 요약", 42L);

        patchSummary(ownerToken, id, Map.of("summary", "사용자가 고친 요약"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("사용자가 고친 요약"));

        assertThat(storedSummary(id)).isEqualTo("사용자가 고친 요약");
        // 커서까지 따라 움직이면(=지금 시점으로 밀면) 아직 요약 안 된 최근 대화가 요약된 것으로
        // 취급돼 프롬프트에서 통째로 사라진다. 수동 조각은 갈아 끼운 조각의 구간을 물려받는다.
        assertThat(storedCursor(id)).isEqualTo(42L);
    }

    @Test
    @DisplayName("장기기억을 빈 문자열로 저장하면 조각이 전부 지워지고 커서도 0으로 돌아간다")
    void update_summary_with_empty_clears_it() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "옛 요약", 42L);

        patchSummary(ownerToken, id, Map.of("summary", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").doesNotExist());

        // 조각을 통째로 지우므로 커서도 함께 0으로 돌아간다. 블롭 시절의 High 버그
        // ("요약문만 비우고 커서를 남겨 커서 앞 구간이 프롬프트에서 증발")가 여기서 소멸한다 —
        // 커서가 별도 필드가 아니라 조각에서 유도되는 값이 되면서 어긋날 방법 자체가 없어졌다.
        assertThat(storedSummary(id)).isNull();
        assertThat(storedCursor(id)).isZero();
    }

    @Test
    @DisplayName("공백만 보내도 null로 지워진다 (trim 후 판정)")
    void update_summary_with_blanks_clears_it() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "옛 요약", 42L);

        // trim 전 값으로 비었는지 판정하면 여기서 빈 문자열이 저장돼, "없음"의 표현이 두 개가 된다
        patchSummary(ownerToken, id, Map.of("summary", "  \n  "))
                .andExpect(status().isOk());

        assertThat(storedSummary(id)).isNull();
    }

    @Test
    @DisplayName("장기기억 저장 시 앞뒤 공백은 다듬어진다")
    void update_summary_trims_edges() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        // textarea는 끝에 개행이 남기 쉬운데, 그게 매 턴 프롬프트에 그대로 실린다
        patchSummary(ownerToken, id, Map.of("summary", "  기억할 내용  \n\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("기억할 내용"));

        assertThat(storedSummary(id)).isEqualTo("기억할 내용");
    }

    @Test
    @DisplayName("summary 필드가 없으면 400이고 기존 장기기억이 지워지지 않는다")
    void update_summary_without_field_returns_400_and_keeps_summary() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "지켜야 할 기억", 42L);

        patchSummary(ownerToken, id, Collections.emptyMap()).andExpect(status().isBadRequest());

        assertThat(storedSummary(id))
                .isEqualTo("지켜야 할 기억");
    }

    @Test
    @DisplayName("예산을 넘는 장기기억은 400이고 저장되지 않는다")
    void update_summary_over_budget_returns_400() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "지켜야 할 기억", 42L);

        // 상한이 문자수(@Size 3000)에서 토큰 예산(SummarySelector.TOKEN_BUDGET)으로 바뀌었다.
        // 한글은 대략 글자당 1.15토큰이라 실질 상한은 약 3,034자 — 프론트 MEMORY_MAX(3000)가
        // 더 빡빡한 쪽이라 사용자는 여전히 프론트에서 먼저 막힌다(백엔드가 더 느슨해야 안전하다).
        String tooLong = "가".repeat(4000);
        assertThat(SummarySelector.estimateTokens(tooLong)).isGreaterThan(SummarySelector.TOKEN_BUDGET);

        patchSummary(ownerToken, id, Map.of("summary", tooLong))
                .andExpect(status().isBadRequest());

        assertThat(storedSummary(id))
                .isEqualTo("지켜야 할 기억");
    }

    @Test
    @DisplayName("예산 안쪽 길이는 저장된다 (문자수 3000 상한 시절에는 막히던 길이)")
    void update_summary_just_under_budget_is_saved() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);

        // 옛 @Size(max=3000)이면 400이던 길이. 예산 환산으로는 아직 안쪽이라 통과해야 한다.
        String memory = "가".repeat(3010);
        assertThat(SummarySelector.estimateTokens(memory)).isLessThanOrEqualTo(SummarySelector.TOKEN_BUDGET);

        patchSummary(ownerToken, id, Map.of("summary", memory)).andExpect(status().isOk());

        assertThat(storedSummary(id)).isEqualTo(memory);
    }

    @Test
    @DisplayName("남의 챗룸 장기기억 조회·수정은 차단되고 내용이 바뀌지 않는다")
    void others_summary_is_blocked() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        seedSummary(id, "남의 기억", 42L);

        mockMvc.perform(get("/chatrooms/{id}/summary", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        patchSummary(otherToken, id, Map.of("summary", "해킹"))
                .andExpect(status().isForbidden());

        assertThat(storedSummary(id)).isEqualTo("남의 기억");
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
    @DisplayName("메시지가 있는 챗룸도 삭제되고 메시지도 함께 지워진다")
    void delete_chatroom_with_messages_cascades() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        ChatRoom room = chatRoomRepository.findById(id).orElseThrow();
        messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content("안녕")
                .build());

        mockMvc.perform(delete("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(chatRoomRepository.findById(id)).isEmpty();
        assertThat(messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(id)).isEmpty();
    }

    @Test
    @DisplayName("챗룸을 삭제하면 요약 조각도 함께 지워진다")
    void delete_chatroom_cascades_to_summary_fragments() throws Exception {
        long id = createChatRoom(ownerToken, ownerCharacterId);
        ChatRoom room = chatRoomRepository.findById(id).orElseThrow();
        Message message = messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content("안녕")
                .build());
        seedSummaryFragment(id, "접어둔 옛 이야기", message.getId(), message.getId());

        mockMvc.perform(delete("/chatrooms/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // 조각은 메시지를 FK로 참조하지 않으므로(삭제 규칙을 일관되게 두려고 일부러 뺐다),
        // 방이 사라질 때 같이 지워지는 건 chat_room 쪽 ON DELETE CASCADE 하나뿐이다.
        assertThat(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(id)).isEmpty();
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
                .greeting("안녕하세요")
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

    // GET/PATCH는 아직 방의 요약문과 커서를 읽고 쓰지만, 그 값의 출처는 이제 조각이다.
    // 둘을 함께 심어야 상태가 어긋나지 않는다(운영 코드도 조각에서 블롭을 다시 만든다).
    private void seedSummary(long roomId, String summary, Long upToId) {
        seedSummaryFragment(roomId, summary, 1L, upToId);
    }

    /** 방에 실제로 남아 있는 조각을 이어 붙인 것. 미러가 없어졌으므로 여기가 유일한 확인처다. */
    private String storedSummary(long roomId) {
        return SummarySelector.join(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId));
    }

    private Long storedCursor(long roomId) {
        return SummarySelector.cursorOf(chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId));
    }

    private void seedSummaryFragment(long roomId, String content, long fromId, long toId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        chatSummaryRepository.save(ChatSummary.builder()
                .chatRoom(room)
                .seq(1)
                .fromMessageId(fromId)
                .toMessageId(toId)
                .content(content)
                .importance(ChatSummary.DEFAULT_IMPORTANCE)
                .keywords(List.of("씨앗"))
                .estimatedTokens(20)
                .build());
    }

    private ResultActions patchSummary(String token, long id, Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch("/chatrooms/{id}/summary", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions patchModel(String token, long id, Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch("/chatrooms/{id}/model", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions patchThinking(String token, long id, Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch("/chatrooms/{id}/thinking", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}