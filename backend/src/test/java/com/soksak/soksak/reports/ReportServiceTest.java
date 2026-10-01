package com.soksak.soksak.reports;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.message.MessageRole;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock CharacterRepository characterRepository;
    @Mock UserRepository userRepository;
    @Mock ChatRoomService chatRoomService;
    @Mock MessageRepository messageRepository;

    ReportService service;
    User owner;
    User reporter;
    ChatCharacter character;

    @BeforeEach
    void setUp() {
        service = new ReportService(reportRepository, characterRepository, userRepository,
                chatRoomService, messageRepository, 3, 30);
        owner = user(1L, "owner");
        reporter = user(2L, "reporter");
        character = ChatCharacter.builder().id(10L).user(owner).name("여우").description("d")
                .persona("p").greeting("g").imageUrl("/api/uploads/a.png").build();
        // 오래전에 만들어져 고친 적 없는 캐릭터 — 창 시작은 "지금 - 30일"이 된다.
        ReflectionTestUtils.setField(character, "createdAt", LocalDateTime.now().minusDays(100));
        lenient().when(characterRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(character));
        lenient().when(userRepository.findByLoginId("reporter")).thenReturn(Optional.of(reporter));
    }

    private static User user(Long id, String loginId) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "loginId", loginId);
        return u;
    }

    private void reportersInWindow(long n) {
        when(reportRepository.countReportersSince(eq(10L), any())).thenReturn(n);
    }

    @Test
    @DisplayName("창 안의 서로 다른 신고자가 2명이면 숨기지 않는다")
    void two_reporters_not_hidden() {
        reportersInWindow(2);

        service.reportCharacter("reporter", 10L, ReportTarget.CONCEPT, ReportReason.SEXUAL, null);

        assertThat(character.isHidden()).isFalse();
        verify(reportRepository).save(any());
    }

    @Test
    @DisplayName("3명째에 숨긴다")
    void third_reporter_hides() {
        reportersInWindow(3);

        service.reportCharacter("reporter", 10L, ReportTarget.IMAGE, ReportReason.VIOLENCE, null);

        assertThat(character.isHidden()).isTrue();
    }

    @Test
    @DisplayName("MINOR도 1건으로는 숨기지 않는다 — 다른 사유와 같은 신고자 수 기준")
    void minor_uses_same_threshold() {
        reportersInWindow(1);

        service.reportCharacter("reporter", 10L, ReportTarget.IMAGE, ReportReason.MINOR, null);

        assertThat(character.isHidden()).isFalse();
    }

    @Test
    @DisplayName("같은 사람의 재신고도 매번 저장한다 — 판정은 distinct 쿼리가 사람 단위로 센다")
    void repeat_report_is_saved() {
        reportersInWindow(1);

        service.reportCharacter("reporter", 10L, ReportTarget.CONCEPT, ReportReason.HATE, "x");
        service.reportCharacter("reporter", 10L, ReportTarget.CONCEPT, ReportReason.HATE, "x");

        verify(reportRepository, org.mockito.Mockito.times(2)).save(any());
        assertThat(character.isHidden()).isFalse();
    }

    @Test
    @DisplayName("고친 적 없으면 창 시작은 지금 - 30일")
    void window_starts_30_days_ago() {
        reportersInWindow(0);

        service.reportCharacter("reporter", 10L, ReportTarget.CONCEPT, ReportReason.OTHER, null);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportRepository).countReportersSince(eq(10L), since.capture());
        assertThat(since.getValue()).isCloseTo(LocalDateTime.now().minusDays(30), within(5, SECONDS));
    }

    @Test
    @DisplayName("최근에 고쳤으면 창 시작은 고친 시각 — 그 전 신고는 세지 않는다")
    void window_starts_at_last_edit() {
        character.update("여우", "d", "p2", "g", "/api/uploads/b.png", null);
        LocalDateTime edited = character.getContentUpdatedAt();
        reportersInWindow(1);

        service.reportCharacter("reporter", 10L, ReportTarget.CONCEPT, ReportReason.OTHER, null);

        verify(reportRepository).countReportersSince(10L, edited);
        assertThat(character.isHidden()).isFalse();
    }

    @Test
    @DisplayName("고쳐도 숨김은 풀리지 않는다")
    void edit_does_not_unhide() {
        character.hide();
        character.update("여우", "d", "p2", "g", null, null);

        assertThat(character.isHidden()).isTrue();
    }

    @Test
    @DisplayName("스냅샷: IMAGE는 신고 시점의 image_url을 남긴다")
    void image_snapshot() {
        reportersInWindow(0);

        service.reportCharacter("reporter", 10L, ReportTarget.IMAGE, ReportReason.SEXUAL, null);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getContentSnapshot()).isEqualTo("/api/uploads/a.png");
        assertThat(saved.getValue().getReporter()).isSameAs(reporter);
    }

    @Test
    @DisplayName("본인 캐릭터는 신고할 수 없다")
    void own_character_rejected() {
        assertThatThrownBy(() -> service.reportCharacter("owner", 10L, ReportTarget.IMAGE, ReportReason.SEXUAL, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_OWN_CHARACTER);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("캐릭터 경로로 CHAT 신고는 받지 않는다")
    void chat_target_on_character_rejected() {
        assertThatThrownBy(() -> service.reportCharacter("reporter", 10L, ReportTarget.CHAT, ReportReason.SEXUAL, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ── 채팅 응답 신고 ──

    private ChatRoom room() {
        return ChatRoom.builder().id(5L).user(reporter).character(character).title("여우").build();
    }

    private Message message(Long id, ChatRoom room, MessageRole role, String content) {
        Message m = Message.builder().chatRoom(room).role(role).content(content).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    @DisplayName("채팅 신고는 MINOR여도 캐릭터를 숨기지 않고, 앞 사용자 메시지와 응답을 스냅샷으로 남긴다")
    void chat_report_never_hides() {
        ChatRoom room = room();
        when(chatRoomService.getOwnedChatRoomWithDetails("reporter", 5L)).thenReturn(room);
        when(messageRepository.findById(42L)).thenReturn(Optional.of(message(42L, room, MessageRole.ASSISTANT, "응답")));
        when(messageRepository.findFirstByChatRoomIdAndIdLessThanAndRoleOrderByIdDesc(5L, 42L, MessageRole.USER))
                .thenReturn(Optional.of(message(41L, room, MessageRole.USER, "질문")));

        service.reportMessage("reporter", 5L, 42L, ReportReason.MINOR, null);

        assertThat(character.isHidden()).isFalse();
        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getTarget()).isEqualTo(ReportTarget.CHAT);
        assertThat(saved.getValue().getMessageId()).isEqualTo(42L);
        assertThat(saved.getValue().getCharacter()).isSameAs(character);
        assertThat(saved.getValue().getContentSnapshot()).contains("질문").contains("응답");
    }

    @Test
    @DisplayName("사용자 메시지는 신고할 수 없다")
    void user_message_rejected() {
        ChatRoom room = room();
        when(chatRoomService.getOwnedChatRoomWithDetails("reporter", 5L)).thenReturn(room);
        when(messageRepository.findById(41L)).thenReturn(Optional.of(message(41L, room, MessageRole.USER, "질문")));

        assertThatThrownBy(() -> service.reportMessage("reporter", 5L, 41L, ReportReason.OTHER, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_ASSISTANT);
    }

    @Test
    @DisplayName("다른 방의 메시지는 신고할 수 없다")
    void other_room_message_rejected() {
        ChatRoom mine = room();
        ChatRoom other = ChatRoom.builder().id(6L).user(owner).character(character).title("x").build();
        when(chatRoomService.getOwnedChatRoomWithDetails("reporter", 5L)).thenReturn(mine);
        when(messageRepository.findById(99L)).thenReturn(Optional.of(message(99L, other, MessageRole.ASSISTANT, "남의 응답")));

        assertThatThrownBy(() -> service.reportMessage("reporter", 5L, 99L, ReportReason.OTHER, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_FORBIDDEN);
        verify(reportRepository, never()).save(any());
    }
}
