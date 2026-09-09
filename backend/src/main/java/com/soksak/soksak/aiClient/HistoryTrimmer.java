package com.soksak.soksak.aiClient;

import com.soksak.soksak.message.Message;

import java.util.List;

/**
 * 프롬프트에 실을 최근 대화를 토큰 예산 안으로 자른다.
 *
 * 개수로 끊으면 안 된다 — 50자짜리 턴과 2000자짜리 턴의 무게가 전혀 다르다.
 * 요약이 밀리거나 실패하면 미요약분이 계속 쌓이는데, 그때 프롬프트가 무한정 커지는 것을
 * 막는 상한이다. 평소에는 예산에 닿지 않으므로 아무 일도 하지 않는다.
 */
public final class HistoryTrimmer {

    // 최근 대화에 허용하는 토큰 예산.
    // ⚠️ SummaryPlan.WINDOW(=20)와 한 세트다. WINDOW 는 "최근 20턴은 요약하지 않는다"는
    // 약속인데, 이 예산이 20턴을 못 덮으면 잘린 메시지는 요약에도 없고 프롬프트에도 없는
    // 영구 구멍이 된다. 한국어 20턴 ≈ 400자 × 20 × 1.15 ≈ 9,400토큰이라 여유를 둬 12,000.
    // 목표치가 아니라 천장이다 — 줄이려면 WINDOW 를 같이 줄여야 한다.
    private static final int TOKEN_BUDGET = 12_000;

    // 2026-09-01 실측(claude-opus-4-8, 한국어): 본문 글자당 1.12토큰, 메시지당 오버헤드 7.
    // 둘 다 올려 잡아 과대추정 쪽으로 기울였다 — 과소추정만 컨텍스트 초과 사고를 낸다.
    // ai-server 의 token_counter.py 와 같은 식이다.
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
            // 요약 조각과 달리 여기서는 건너뛰지 않고 멈춘다 — 대화 중간을 빼면 문맥이
            // 끊기고 user/assistant 교대도 어긋난다. 잘린다면 반드시 앞쪽(오래된 쪽)이다.
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