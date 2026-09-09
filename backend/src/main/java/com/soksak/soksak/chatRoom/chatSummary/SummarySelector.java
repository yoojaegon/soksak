package com.soksak.soksak.chatRoom.chatSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SummarySelector {
    private SummarySelector() {}

    /**
     * 프롬프트의 기억 자리에 허용하는 토큰 예산.
     * 표시(GET)·주입(프롬프트)·수정 상한(PATCH)이 모두 이 하나를 본다 —
     * "보이는 것 = 쓰이는 것 = 고칠 수 있는 것"이 어긋나면
     * GET에는 나오는데 PATCH로는 못 고치는 기억이 생긴다.
     */
    public static final int TOKEN_BUDGET = 3500;

    /** 이 이상이면 나이와 무관하게 후보가 된다. 사용자가 손으로 쓴 기억도 이 값을 받는다. */
    public static final int PINNED_IMPORTANCE = 4;

    // 2026-09-01 실측(claude-opus-4-8, 한국어): 본문 글자당 1.12토큰 + 메시지당 오버헤드 7.
    // 둘 다 올려 잡아 과대추정 쪽으로 기울였다 — 과소추정만 컨텍스트 초과 사고를 낸다.
    private static final double TOKENS_PER_CHAR = 1.15;
    private static final int OVERHEAD = 10;

    public static List<ChatSummary> select(List<ChatSummary> summaries) {
        return select(summaries, TOKEN_BUDGET);
    }

    static List<ChatSummary> select(List<ChatSummary> summaries, int budget) {
        if (summaries == null || summaries.isEmpty()) return List.of();

        List<ChatSummary> picked = new ArrayList<>();

        int used = fill(summaries, picked, 0, budget, true);
        fill(summaries, picked, used, budget, false);

        if (picked.isEmpty()) {
            picked.add(summaries.get(summaries.size() - 1));
        }

        picked.sort(Comparator.comparingInt(ChatSummary::getSeq));
        return picked;
    }

    private static int fill(List<ChatSummary> summaries, List<ChatSummary> picked,
                            int used, int budget, boolean pinnedOnly) {
        for (int i = summaries.size() - 1; i >= 0; i--) {
            ChatSummary summary = summaries.get(i);
            boolean pinned = summary.getImportance() >= PINNED_IMPORTANCE;
            if (pinned != pinnedOnly) continue;
            if (used + summary.getEstimatedTokens() > budget) continue;
            picked.add(summary);
            used += summary.getEstimatedTokens();
        }
        return used;
    }

    /**
     * 요약이 끝난 지점 = 조각 전체의 마지막 to_message_id. 조각은 뒤로만 붙고 삭제는 꼬리부터
     * 잘리므로 마지막 조각이 곧 최대값이다.
     *
     * ⚠️ 반드시 <b>전체</b> 목록으로 계산할 것 — select()가 돌려준 것으로 잡으면 안 된다.
     * 예산에 밀려 빠진 조각의 구간이 미요약분으로 되살아나 요약과 원문이 이중으로 실린다.
     * 요약 대상(SummaryPlan)과 프롬프트 대상이 갈리지 않도록 커서의 출처는 여기 하나뿐이다.
     */
    public static long cursorOf(List<ChatSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) return 0;
        return summaries.get(summaries.size() - 1).getToMessageId();
    }

    /**
     * 조각을 만들기 전에(=아직 ai-server의 token_count 값이 없을 때) 쓰는 근사식.
     * 사용자가 손으로 쓴 기억의 토큰 수와 그 상한 검사가 이 값을 쓴다.
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() * TOKENS_PER_CHAR) + OVERHEAD;
    }

    public static String join(List<ChatSummary> selected) {
        if (selected == null || selected.isEmpty()) return null;
        return selected.stream()
                .map(ChatSummary::getContent)
                .collect(Collectors.joining("\n\n"));
    }
}
