package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.SummarySelector;

import java.util.List;

public record SummaryPlan (
        List<String> previousSummaries,
        List<Message> batch,
        Long fromId,
        Long upToId,
        int nextSeq
){
    // 최근 WINDOW개는 원문 그대로 프롬프트에 실리므로 요약 대상에서 뺀다.
    // ⚠️ HistoryTrimmer.TOKEN_BUDGET 이 이 개수를 덮을 만큼 커야 한다. 아니면 잘린 메시지가
    // 요약에도 프롬프트에도 없는 구멍이 된다.
    private static final int WINDOW = 20;
    // 요약 대기분이 이만큼 모여야 굴린다 — 한두 개씩 굴리면 LLM 호출만 잦아진다.
    private static final int BATCH = 10;
    // 한 번에 넘길 상한. 밀린 게 많아도 여기서 끊고 나머지는 다음 턴에.
    private static final int MAX_BATCH = 30;
    // 맥락 파악용으로만 딸려 보내는 직전 조각 수. 더 보내면 요약 입력이 매 턴 커져 갱신형으로
    // 되돌아가는 셈이다 — ai-server도 이걸 "다시 옮겨 적지 말 것"으로만 쓴다.
    private static final int CONTEXT_SUMMARIES = 2;
    // 첫 조각의 seq. 유니크 제약이 (chat_room_id, seq)라 방마다 1부터 센다.
    private static final int FIRST_SEQ = 1;

    // 요약할 때가 아니면 null. 전송 경로가 이미 읽어온 이력과 조각을 그대로 받아 쓰므로 추가 조회가 없다.
    // summaries는 seq 오름차순 전제(findByChatRoomIdOrderBySeqAsc).
    public static SummaryPlan of(List<Message> history, List<ChatSummary> summaries) {
        int summarizableEnd = history.size() - WINDOW;
        if (summarizableEnd <= 0) return null;

        // 커서는 더 이상 방의 필드가 아니라 조각에서 나오는 파생값이다. 프롬프트를 만드는
        // buildRequest도 같은 함수를 쓴다 — 갈리면 요약 대상과 프롬프트 대상이 어긋난다.
        ChatSummary last = summaries.isEmpty() ? null : summaries.get(summaries.size() - 1);
        long upTo = SummarySelector.cursorOf(summaries);

        List<Message> batch = history.subList(0, summarizableEnd).stream()
                .filter(m -> m.getId() > upTo)
                .limit(MAX_BATCH)
                .toList();

        if (batch.size() < BATCH) return null;

        return new SummaryPlan(
                recentContents(summaries),
                batch,
                batch.get(0).getId(),
                batch.get(batch.size() - 1).getId(),
                last == null ? FIRST_SEQ : last.getSeq() + 1
        );
    }

    private static List<String> recentContents(List<ChatSummary> summaries) {
        return summaries.stream()
                .skip(Math.max(0, summaries.size() - CONTEXT_SUMMARIES))
                .map(ChatSummary::getContent)
                .toList();
    }
}