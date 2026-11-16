package com.soksak.soksak.aiClient;

import com.soksak.soksak.message.Message;

import java.util.List;

/**
 * 프롬프트에 실을 최근 대화를 토큰 예산 안으로 자른다.
 *
 * 개수로 자르지 않는 건 50자짜리 턴과 2000자짜리 턴의 무게가 전혀 다르기 때문이다.
 * 요약이 밀리거나 실패하면 요약 안 된 대화가 계속 쌓이는데, 그때 프롬프트가 끝없이 커지지 않게
 * 막는 상한이다. 평소에는 예산에 닿지 않아서 아무것도 자르지 않는다.
 */
public final class HistoryTrimmer {

    /**
     * 최근 대화에 허용하는 토큰 예산. 목표치가 아니라 상한이다.
     *
     * ⚠️ SummaryPlan.WINDOW(=20)와 한 세트다. WINDOW는 최근 20턴은 요약하지 않는다는 약속이라,
     * 이 예산이 20턴을 못 덮으면 잘린 메시지는 요약에도 프롬프트에도 없게 된다.
     * 한국어 20턴은 400자 × 20 × 1.15 ≈ 9,400토큰이라 여유를 둬서 12,000으로 잡았다.
     * 줄이려면 WINDOW도 같이 줄여야 한다.
     */
    private static final int TOKEN_BUDGET = 12_000;

    /**
     * 한국어 토큰 근사식. 적게 세면 컨텍스트를 넘길 수 있어서 실측값보다 조금 올려 잡았다.
     * ai-server의 token_counter.py, SummarySelector와 같은 값이다. 측정값은 docs/measurements.md에 있다.
     */
    private static final double TOKENS_PER_CHAR = 1.15;
    private static final int MESSAGE_OVERHEAD = 10;

    private HistoryTrimmer() {}

    public static List<Message> trim(List<Message> messages) {
        return trim(messages, TOKEN_BUDGET);
    }

    /** 예산을 인자로 받는 본체. 테스트가 작은 예산으로 경계를 재현할 수 있게 열어 둔다. */
    static List<Message> trim(List<Message> messages, int budget) {
        if (messages == null || messages.isEmpty()) return List.of();

        int used = 0;
        int start = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int cost = estimate(messages.get(i).getContent());
            // 요약 조각과 달리 건너뛰지 않고 여기서 멈춘다. 대화 중간이 빠지면 문맥이 끊기고
            // user/assistant 순서도 어긋나므로, 자른다면 항상 앞쪽(오래된 쪽)이다.
            if (used + cost > budget) break;
            used += cost;
            start = i;
        }

        // 메시지 하나가 예산보다 큰 경우(집필 모드의 긴 답변)에도 직전 턴은 남긴다.
        if (start == messages.size()) start = messages.size() - 1;

        return List.copyOf(messages.subList(start, messages.size()));
    }

    private static int estimate(String content) {
        if (content == null) return MESSAGE_OVERHEAD;
        return (int) Math.ceil(content.length() * TOKENS_PER_CHAR) + MESSAGE_OVERHEAD;
    }
}
