package com.soksak.soksak.aiClient;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 채팅 모델 카탈로그 — 선택지·표시명·기본값의 단일 출처(제품 정책은 백엔드 소유).
 * <p>
 * slug는 게이트웨이 시절 표기를 물려받은 소삭 내부 식별자다(chat_room.model 에 이 값이 저장돼
 * 있어 바꾸면 마이그레이션이 필요하다). 제공사 실제 모델 ID로의 변환은 ai-server 의
 * {@code llm/factory.py} 가 갖는다 — 예: {@code anthropic/claude-opus-4.8} → {@code claude-opus-4-8}.
 */
@Slf4j
public final class ModelCatalog {

    public record Entry(String id, String label) {}

    // 두 제공사 키로 실제 호출해 응답을 확인한 모델만 올린다(2026-09-09). 순서가 곧 픽커 노출 순서.
    // 빠진 것들: openai/* 는 키가 없고, google/gemini-2.5-pro 는 신규 사용자에게 폐기돼 404다.
    private static final List<Entry> ENTRIES = List.of(
            new Entry("anthropic/claude-opus-4.8", "Claude Opus 4.8"),
            new Entry("anthropic/claude-opus-4.7", "Claude Opus 4.7"),
            new Entry("anthropic/claude-opus-4.6", "Claude Opus 4.6"),
            new Entry("anthropic/claude-haiku-4.5", "Claude Haiku 4.5"),
            new Entry("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            new Entry("google/gemini-3.5-flash", "Gemini 3.5 Flash"),
            new Entry("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite")
    );

    // 저가 모델. 실사용 모델로 올리려면 이 상수만 바꾸면 된다. ai-server 의 CHAT_ 프로필
    // 기본값(main.py)도 같은 slug 로 맞춰 둔다.
    private static final String DEFAULT_ID = "google/gemini-3.5-flash-lite";

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
