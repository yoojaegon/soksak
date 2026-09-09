package com.soksak.soksak.aiClient;

import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryTrimmerTest {

    // 자르기에 쓰이는 값은 content 길이뿐이다. 저장하지 않으므로 chatRoom 은 null 이어도 무방하다.
    // 메시지 하나의 비용 = ceil(글자수 × 1.15) + 10 → 10자면 22토큰.
    private Message message(String content) {
        return Message.builder()
                .role(MessageRole.USER)
                .content(content)
                .build();
    }

    private Message ofLength(String label, int length) {
        return message(label + "가".repeat(length - label.length()));
    }

    @Test
    @DisplayName("예산 안에 들어오면 전부 남긴다")
    void keeps_everything_within_budget() {
        List<Message> messages = List.of(
                ofLength("1", 10), ofLength("2", 10), ofLength("3", 10));

        List<Message> trimmed = HistoryTrimmer.trim(messages, 10_000);

        assertThat(trimmed).hasSize(3);
    }

    @Test
    @DisplayName("예산을 넘으면 오래된 쪽부터 잘린다")
    void drops_oldest_messages_first() {
        // 10자 = 22토큰. 예산 50이면 최신 두 개(44)까지만 들어간다.
        List<Message> messages = List.of(
                ofLength("1", 10), ofLength("2", 10), ofLength("3", 10));

        List<Message> trimmed = HistoryTrimmer.trim(messages, 50);

        assertThat(trimmed).extracting(Message::getContent)
                .allSatisfy(content -> assertThat(content).doesNotStartWith("1"));
        assertThat(trimmed).hasSize(2);
    }

    @Test
    @DisplayName("잘라도 남은 메시지의 순서는 그대로다")
    void preserves_chronological_order() {
        List<Message> messages = List.of(
                ofLength("1", 10), ofLength("2", 10), ofLength("3", 10));

        List<Message> trimmed = HistoryTrimmer.trim(messages, 50);

        assertThat(trimmed).extracting(m -> m.getContent().substring(0, 1))
                .containsExactly("2", "3");
    }

    @Test
    @DisplayName("예산을 넘는 메시지를 만나면 그 앞은 건너뛰지 않고 멈춘다")
    void stops_instead_of_skipping_a_large_message() {
        // 대화 중간을 빼면 문맥이 끊기고 user/assistant 교대도 어긋난다.
        // 요약 조각(SummarySelector)이 건너뛰는 것과 반대되는 규칙이다.
        List<Message> messages = List.of(
                ofLength("1", 10), ofLength("2", 100), ofLength("3", 10));

        List<Message> trimmed = HistoryTrimmer.trim(messages, 50);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0).getContent()).startsWith("3");
    }

    @Test
    @DisplayName("메시지 하나가 예산보다 커도 직전 턴은 남긴다")
    void keeps_the_latest_message_even_if_it_exceeds_budget() {
        List<Message> messages = List.of(ofLength("1", 100), ofLength("2", 100));

        List<Message> trimmed = HistoryTrimmer.trim(messages, 50);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0).getContent()).startsWith("2");
    }

    @Test
    @DisplayName("기본 예산은 요약에서 제외되는 최근 20턴을 덮는다")
    void default_budget_covers_the_summary_window() {
        // SummaryPlan.WINDOW = 20 과 한 세트다. 여기서 잘리는 메시지는 요약에도 없고
        // 프롬프트에도 없는 구멍이 되므로, 20턴은 반드시 통째로 들어와야 한다.
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(ofLength(String.valueOf(i), 400));
        }

        assertThat(HistoryTrimmer.trim(messages)).hasSize(20);
    }

    @Test
    @DisplayName("메시지가 없으면 빈 목록을 돌려준다")
    void returns_empty_when_no_messages() {
        assertThat(HistoryTrimmer.trim(List.of())).isEmpty();
    }
}
