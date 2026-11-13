package com.soksak.soksak.chatRoom.chatSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 채팅방의 요약 조각 중 프롬프트에 실을 것을 고르고,
 * 요약이 어디까지 됐는지와 토큰 수 어림값을 계산한다.
 */
public class SummarySelector {
    private SummarySelector() {}

    /**
     * 프롬프트의 기억 자리에 쓰는 토큰 예산.
     * 화면 표시(GET), 프롬프트 주입, 사용자 수정 상한(PATCH)이 모두 이 값을 본다.
     * 셋이 같은 값을 써야 보이는 기억, 실제로 쓰이는 기억, 고칠 수 있는 기억이 같아진다.
     */
    public static final int TOKEN_BUDGET = 3500;

    /**
     * 중요도가 이 값 이상이면 오래된 조각이어도 먼저 고른다.
     * 사용자가 직접 쓴 기억도 이 값으로 저장한다.
     */
    public static final int PINNED_IMPORTANCE = 4;

    // 한국어 실측값은 글자당 1.12토큰, 메시지당 오버헤드 7토큰이다(2026-09-01, claude-opus-4-8).
    // 적게 잡으면 컨텍스트를 넘길 수 있어서 둘 다 조금 올려 잡았다.
    private static final double TOKENS_PER_CHAR = 1.15;
    private static final int OVERHEAD = 10;

    /**
     * 예산 안에서 실을 조각을 골라 seq 순으로 돌려준다.
     * 중요도가 높은 조각을 최신순으로 먼저 채우고, 남은 예산을 나머지 조각으로 최신순으로 채운다.
     * 하나도 못 고르면 예산을 넘더라도 가장 최근 조각 하나는 싣는다.
     */
    public static List<ChatSummary> select(List<ChatSummary> summaries) {
        return select(summaries, TOKEN_BUDGET);
    }

    /** 예산을 바꿔 테스트할 수 있게 패키지 범위로 열어 둔다. */
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

    /**
     * 최신 조각부터 훑으며 예산에 들어가는 것을 담고, 지금까지 쓴 토큰 합을 돌려준다.
     * 안 들어가는 조각은 건너뛰고 그보다 오래된 조각을 계속 본다.
     */
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
     * 요약이 끝난 메시지 id. 조각은 뒤로만 붙고 지울 때도 뒤에서부터 지우므로
     * 마지막 조각의 to_message_id가 곧 최댓값이다.
     *
     * ⚠️ 꼭 조각 전체 목록을 넘겨야 한다. select()가 고른 목록으로 계산하면
     * 예산에 밀린 조각의 구간이 아직 요약 안 된 것으로 보여서 요약과 원문이 함께 실린다.
     * 요약할 범위(SummaryPlan)와 프롬프트에 실을 범위가 같은 커서를 보도록 커서는 여기서만 계산한다.
     */
    public static long cursorOf(List<ChatSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) return 0;
        return summaries.get(summaries.size() - 1).getToMessageId();
    }

    /**
     * 글자 수로 토큰 수를 어림한다.
     * ai-server가 잰 token_count가 없는 사용자 작성 기억의 토큰 수와 수정 상한 검사에 쓴다.
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() * TOKENS_PER_CHAR) + OVERHEAD;
    }

    /** 고른 조각의 본문을 빈 줄로 이어 하나의 문자열로 만든다. 고른 게 없으면 null. */
    public static String join(List<ChatSummary> selected) {
        if (selected == null || selected.isEmpty()) return null;
        return selected.stream()
                .map(ChatSummary::getContent)
                .collect(Collectors.joining("\n\n"));
    }
}
