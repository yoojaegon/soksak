package com.soksak.soksak.credit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.auth.RefreshTokenRepository;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.character.Genre;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import com.soksak.soksak.userPersona.UserPersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마디가 채팅 경로에 실제로 물려 있는지 — 차감·거절·잔액 조회를 API 바깥에서 확인한다.
 * <p>
 * {@code CreditServiceTest}가 원자성을 보는 반면 여기선 "보내면 줄어든다"와 "없으면 402"를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditChatE2eTest {

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
    @Autowired CreditService creditService;

    // 실패를 만들어 내야 해서 test 프로필의 스텁 대신 목으로 바꿔 끼운다.
    // 기본 동작은 setUp에서 스텁과 같게 맞춰 둔다 — 환불 테스트에서만 던지게 덮어쓴다.
    @MockitoBean ChatAiClient chatAiClient;

    private static final String LOGIN_ID = "payer";
    private static final String PASSWORD = "pw123456";

    private String token;
    private User user;
    private long roomId;

    @BeforeEach
    void setUp() throws Exception {
        chatSummaryRepository.deleteAll();
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        characterRepository.deleteAll();
        userPersonaRepository.deleteAll();   // 가입 테스트가 기본 페르소나를 만든다
        refreshTokenRepository.deleteAll();
        creditLedgerRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .loginId(LOGIN_ID).email("payer@soksak.com").nickname("내는사람")
                .password(passwordEncoder.encode(PASSWORD)).age(25).gender(Gender.FEMALE)
                .build());

        ChatCharacter character = characterRepository.save(ChatCharacter.builder()
                .user(user).name("릴리").description("설명").persona("페르소나")
                .greeting("안녕").tags(Set.of(Genre.DAILY))
                .build());

        roomId = chatRoomRepository.save(ChatRoom.builder()
                .user(user).character(character).title("릴리").build()).getId();

        token = login();

        given(chatAiClient.reply(any(), any(), any(), any())).willReturn("응답 자리");
    }

    @Test
    @DisplayName("메시지를 보내면 방 모델의 계수만큼 마디가 깎이고 내역이 남는다")
    void sending_a_message_spends_credits() throws Exception {
        creditService.grant(user, 10, CreditReason.MANUAL_GRANT, "테스트");

        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isCreated());

        // 방이 모델을 안 골랐으면 기본 모델(flash-lite)의 계수 1이 적용된다.
        assertThat(balance()).isEqualTo(9);
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.MESSAGE)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getDelta()).isEqualTo(-1);
                    assertThat(row.getChatRoomId()).isEqualTo(roomId);
                });
    }

    @Test
    @DisplayName("마디가 0이면 전송은 402와 INSUFFICIENT_CREDIT을 돌려주고 메시지는 저장되지 않는다")
    void sending_without_credits_returns_402() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CREDIT"));

        assertThat(messageRepository.findAll()).isEmpty();
    }

    // 스트리밍은 emitter를 돌려준 뒤에 본작업이 돌기 때문에, 부족을 스트림 '밖에서' 잡지 못하면
    // 평범한 402가 아니라 SSE event: error로 나간다. 이 테스트가 그 경계를 고정한다.
    @Test
    @DisplayName("스트리밍 전송도 마디가 없으면 스트림을 열기 전에 402로 끊는다")
    void streaming_without_credits_returns_402_before_the_stream_opens() throws Exception {
        mockMvc.perform(post("/chatrooms/{roomId}/messages/stream", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CREDIT"));
    }

    // 답이 안 나온 턴은 받지 않는다 — 사용자가 중간에 나간 경우는 답이 저장되므로 여기 해당하지
    // 않는다(MessageService.sendToken). 이 테스트가 그 둘을 가르는 선을 고정한다.
    @Test
    @DisplayName("AI가 실패하면 깎았던 마디를 돌려주고 원장에 반대 부호의 줄이 남는다")
    void a_failed_turn_is_refunded() throws Exception {
        creditService.grant(user, 10, CreditReason.MANUAL_GRANT, "테스트");
        given(chatAiClient.reply(any(), any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(post("/chatrooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "안녕 릴리야"))))
                .andExpect(status().isServiceUnavailable());

        assertThat(balance()).isEqualTo(10);   // 깎였다가 그대로 되돌아왔다
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.REFUND)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getDelta()).isEqualTo(1);       // 차감 줄은 지우지 않고 반대 줄을 쌓는다
                    assertThat(row.getBalanceAfter()).isEqualTo(10);
                    assertThat(row.getChatRoomId()).isEqualTo(roomId);
                });
    }

    @Test
    @DisplayName("GET /credits/me 는 현재 잔액을 돌려준다")
    void credits_me_returns_balance() throws Exception {
        creditService.grant(user, 7, CreditReason.MANUAL_GRANT, "테스트");

        mockMvc.perform(get("/credits/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(7));
    }

    @Test
    @DisplayName("충전은 묶음 id로만 받는다 — 지급되고 원장에 TOPUP이 남는다")
    void topping_up_grants_the_pack_amount() throws Exception {
        mockMvc.perform(post("/credits/topup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packId", "small"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10));

        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.TOPUP)
                .singleElement()
                .satisfies(row -> assertThat(row.getDelta()).isEqualTo(10));
    }

    // 수량이 아니라 묶음 id를 받는 이유를 고정한다 — 프론트가 숫자를 지어내도 통하면 안 된다.
    @Test
    @DisplayName("없는 묶음으로 충전하면 400이고 잔액은 그대로다")
    void topping_up_with_an_unknown_pack_is_rejected() throws Exception {
        mockMvc.perform(post("/credits/topup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packId", "99999마디"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CREDIT_PACK"));

        assertThat(balance()).isZero();
    }

    @Test
    @DisplayName("가입하면 보너스가 잔액과 원장에 함께 들어온다")
    void signup_grants_the_bonus() throws Exception {
        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", "newbie", "password", "pw123456",
                                "email", "newbie@soksak.com", "nickname", "새사람",
                                "age", 20, "gender", "MALE"))))
                .andExpect(status().isCreated());

        int balance = userRepository.findCreditBalanceByLoginId("newbie").orElseThrow();
        assertThat(balance).isPositive();
        assertThat(creditLedgerRepository.findAll())
                .filteredOn(row -> row.getReason() == CreditReason.SIGNUP_BONUS)
                .singleElement()
                .satisfies(row -> assertThat(row.getDelta()).isEqualTo(balance));
    }

    private int balance() {
        return userRepository.findCreditBalanceById(user.getId()).orElseThrow();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", LOGIN_ID, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
