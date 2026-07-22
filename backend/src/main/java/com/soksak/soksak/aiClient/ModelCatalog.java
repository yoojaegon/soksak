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

    // 게이트웨이 대조가 끝난 모델만 올린다(slug/라벨 모두 GET https://ai-gateway.vercel.sh/v1/models 기준,
    // 2026-07-22 확인 — 버전 표기는 하이픈이 아니라 점이다). 순서가 곧 픽커 노출 순서.
    private static final List<Entry> ENTRIES = List.of(
            new Entry("anthropic/claude-opus-4.8", "Claude Opus 4.8"),
            new Entry("anthropic/claude-opus-4.7", "Claude Opus 4.7"),
            new Entry("anthropic/claude-opus-4.6", "Claude Opus 4.6"),
            new Entry("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            new Entry("google/gemini-3.5-flash", "Gemini 3.5 Flash"),
            new Entry("google/gemini-2.5-pro", "Gemini 2.5 Pro"),
            new Entry("openai/gpt-4o-mini", "GPT-4o mini")
    );

    // 테스트용 저가 모델. 실사용 모델로 올리려면 이 상수만 바꾸면 된다.
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
