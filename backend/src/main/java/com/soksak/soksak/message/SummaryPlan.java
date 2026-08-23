package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoom;

import java.util.List;

public record SummaryPlan (
        String existingSummary,
        List<Message> batch,
        Long upToId
){
    // 최근 WINDOW개는 원문 그대로 프롬프트에 실리므로 요약 대상에서 뺀다.
    private static final int WINDOW = 20;
    // 요약 대기분이 이만큼 모여야 굴린다 — 한두 개씩 굴리면 LLM 호출만 잦아진다.
    private static final int BATCH = 10;
    // 한 번에 넘길 상한. 밀린 게 많아도 여기서 끊고 나머지는 다음 턴에.
    private static final int MAX_BATCH = 30;

    // 요약할 때가 아니면 null. 전송 경로가 이미 읽어온 이력을 그대로 받아 쓰므로 추가 조회가 없다.
    public static SummaryPlan of(ChatRoom room, List<Message> history) {
        int summarizableEnd = history.size() - WINDOW;
        if (summarizableEnd <= 0) return null;

        long upTo = room.getSummarizedUpToId() == null ? 0 : room.getSummarizedUpToId();
        List<Message> batch = history.subList(0, summarizableEnd).stream()
                .filter(m -> m.getId() > upTo)
                .limit(MAX_BATCH)
                .toList();

        if (batch.size() < BATCH) return null;

        return new SummaryPlan(room.getSummary(), batch, batch.get(batch.size() - 1).getId());
    }
}