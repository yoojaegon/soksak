package com.soksak.soksak.chatRoom.chatSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SummarySelector {
    private SummarySelector() {}

    private static final int TOKEN_BUDGET = 3500;
    private static final int PINNED_IMPORTANCE = 4;

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

    public static String join(List<ChatSummary> selected) {
        if (selected == null || selected.isEmpty()) return null;
        return selected.stream()
                .map(ChatSummary::getContent)
                .collect(Collectors.joining("\n\n"));
    }
}
