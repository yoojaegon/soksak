package com.soksak.soksak.aiClient;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 채팅 모델 카탈로그 — 선택지·표시명·기본값의 단일 출처(제품 정책은 백엔드 소유).
 * slug는 Vercel AI Gateway 카탈로그 표기와 일치해야 한다(불일치 시 게이트웨이가 404로 실패).
 */
@Slf4j
public final class ModelCatalog {

    public record Entry(String id, String label) {}

    // 게이트웨이 대조·e2e 검증이 끝난 모델만 올린다. 검증 대기 후보(점/하이픈·preview 표기 확인 필요):
    //   google/gemini-3.1-pro-preview · google/gemini-2.5-pro · google/gemini-3.5-flash
    //   anthropic/claude-opus-4.8 · anthropic/claude-opus-4.7 · anthropic/claude-opus-4.6
    private static final List<Entry> ENTRIES = List.of(
            new Entry("openai/gpt-4o-mini", "GPT-4o mini")
    );

    private static final String DEFAULT_ID = "openai/gpt-4o-mini";

    private ModelCatalog() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static String defaultId() {
        return DEFAULT_ID;
    }

    public static boolean contains(String id) {
        return ENTRIES.stream().anyMatch(entry -> entry.id().equals(id));
    }

    public static String resolve(String slug){
        if (slug == null) return DEFAULT_ID;
        if (!contains(slug)) {
            log.warn("카탈로그에 없는 모델이라 기본값으로 폴백: model={}", slug);
            return DEFAULT_ID;
        }
        return slug;
    }

    public static String orNull(String slug) {
        return (slug != null && contains(slug)) ? slug : null;
    }
}
