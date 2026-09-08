package com.soksak.soksak.chatRoom.chatSummary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummarySelectorTest {

    // 선택에 쓰이는 값(seq/importance/estimatedTokens)만 의미 있게 채운다.
    // 저장하지 않으므로 chatRoom 은 null 이어도 무방하다.
    private ChatSummary fragment(int seq, int importance, int tokens) {
        return ChatSummary.builder()
                .seq(seq)
                .fromMessageId((long) seq)
                .toMessageId((long) seq)
                .content("조각" + seq)
                .importance(importance)
                .estimatedTokens(tokens)
                .build();
    }

    @Test
    @DisplayName("중요도가 높은 옛 조각은 예산이 모자라도 남는다")
    void keeps_important_old_fragment_over_recent_ones() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 5, 30),
                fragment(2, 3, 30),
                fragment(3, 3, 30),
                fragment(4, 3, 30));

        List<ChatSummary> selected = SummarySelector.select(summaries, 60);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(1, 4);
    }

    @Test
    @DisplayName("예산을 넘으면 오래된 조각부터 빠진다")
    void drops_oldest_fragments_when_over_budget() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 3, 30),
                fragment(2, 3, 30),
                fragment(3, 3, 30),
                fragment(4, 3, 30));

        List<ChatSummary> selected = SummarySelector.select(summaries, 60);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(3, 4);
    }

    @Test
    @DisplayName("선택 결과는 항상 seq 오름차순이다")
    void returns_fragments_in_seq_order() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 5, 10),
                fragment(2, 3, 10),
                fragment(3, 5, 10),
                fragment(4, 3, 10));

        List<ChatSummary> selected = SummarySelector.select(summaries, 100);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("예산을 넘는 조각은 건너뛰고 뒤의 작은 조각을 담는다")
    void skips_oversized_fragment_and_keeps_smaller_older_one() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 3, 10),
                fragment(2, 3, 90),
                fragment(3, 3, 20));

        List<ChatSummary> selected = SummarySelector.select(summaries, 40);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(1, 3);
    }

    @Test
    @DisplayName("조각 하나가 예산보다 커도 최신 조각 하나는 싣는다")
    void keeps_latest_fragment_even_if_it_exceeds_budget() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 3, 5000),
                fragment(2, 3, 5000));

        List<ChatSummary> selected = SummarySelector.select(summaries, 3500);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(2);
    }

    @Test
    @DisplayName("중요도가 높아도 예산 안에서만 담는다")
    void caps_important_fragments_by_budget_taking_the_latest() {
        List<ChatSummary> summaries = List.of(
                fragment(1, 5, 30),
                fragment(2, 5, 30),
                fragment(3, 5, 30));

        List<ChatSummary> selected = SummarySelector.select(summaries, 60);

        assertThat(selected).extracting(ChatSummary::getSeq).containsExactly(2, 3);
    }

    @Test
    @DisplayName("조각이 없으면 빈 목록을 돌려준다")
    void returns_empty_when_no_fragments() {
        assertThat(SummarySelector.select(List.of())).isEmpty();
    }

    @Test
    @DisplayName("원본 목록의 순서를 건드리지 않는다")
    void does_not_mutate_the_given_list() {
        // 호출부(PreparedChat.summaries)는 이 뒤로도 계속 쓰이는 살아 있는 목록이다.
        List<ChatSummary> summaries = new ArrayList<>(List.of(
                fragment(1, 5, 10),
                fragment(2, 3, 10),
                fragment(3, 3, 10)));

        SummarySelector.select(summaries, 20);

        assertThat(summaries).extracting(ChatSummary::getSeq).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("고른 조각이 없으면 join 은 null 을 돌려준다")
    void join_returns_null_when_empty() {
        assertThat(SummarySelector.join(List.of())).isNull();
    }

    @Test
    @DisplayName("join 은 조각을 빈 줄로 이어 붙인다")
    void join_concatenates_with_blank_line() {
        List<ChatSummary> selected = List.of(fragment(1, 3, 10), fragment(2, 3, 10));

        assertThat(SummarySelector.join(selected)).isEqualTo("조각1\n\n조각2");
    }
}