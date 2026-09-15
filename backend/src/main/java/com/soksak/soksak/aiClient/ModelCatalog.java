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

    public record Entry(String id, String label, Thinking thinking) {}

    /**
     * 이 모델이 추론에 대해 무엇을 할 수 있는가. 픽커가 노브를 켤지 끌지, 어떤 값을 보여줄지를
     * 이걸로 정하므로 프론트에 사본을 두지 않는다(모델 목록과 같은 규칙).
     * <p>
     * ⚠️ 여기 올리는 건 <b>사용자가 고르는 능력</b>뿐이다. 샘플링 미지원(temperature를 보내면
     * 400)이나 프롬프트 캐싱은 사용자 선택지가 아니라 제공사 사정이라 ai-server가 갖는다 —
     * 올리면 ai-server가 매 요청 그 값을 되받는 역방향 배선이 된다.
     */
    public record Thinking(boolean supported, List<ThinkingLevel> selectable) {
        // 두 제공사의 교집합. Anthropic엔 xhigh/max가, Gemini엔 minimal이 더 있지만 한쪽에만
        // 있는 값을 노출하면 모델을 바꿀 때마다 방의 설정이 무효가 된다.
        private static final List<ThinkingLevel> ALL = List.of(
                ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH);

        /** 추론을 못 하는 모델. 고를 수 있는 건 OFF 하나뿐이다. */
        static Thinking none() {
            return new Thinking(false, List.of(ThinkingLevel.OFF));
        }

        static Thinking of(ThinkingLevel... selectable) {
            return new Thinking(true, List.of(selectable));
        }
    }

    /** 픽커가 "회색으로라도 보여줄" 값의 전체 집합. 못 고르는 값을 숨기면 왜 없는지 알 수 없다. */
    public static List<ThinkingLevel> thinkingLevels() {
        return Thinking.ALL;
    }

    // 두 제공사 키로 실제 호출해 응답을 확인한 모델만 올린다(2026-09-09). 순서가 곧 픽커 노출 순서.
    // 빠진 것들: openai/* 는 키가 없고, google/gemini-2.5-pro 는 신규 사용자에게 폐기돼 404다.
    //
    // 추론 능력은 2026-09-15 실측 기준이다(모델마다 직접 호출해 usage의 추론 토큰을 확인).
    // ⚠️ 판정 기준은 "파라미터를 받아주는가"가 아니라 "추론 토큰이 실제로 나오는가"다 —
    // opus-4.7/4.6은 effort를 200으로 받아주면서 추론은 늘 0이라, 노브를 노출하면 사용자에게
    // 아무 일도 안 일어나는 설정을 파는 셈이 된다. haiku-4.5는 아예 400으로 거절한다.
    //   opus-4.8              effort가 먹는다(추론 61~65)                     → 켜고 끌 수 있다
    //   opus-4.7 / 4.6        effort를 받지만 추론 0 (3회 반복 확인)           → 노출 안 함
    //   haiku-4.5             400 "does not support the effort parameter"     → 노출 안 함
    //   gemini-3.1-pro-preview 기본으로 추론(57). 끄면 400                     → OFF를 못 고른다
    //   gemini-3.5-flash      기본으로 추론(59). thinking_budget=0으로 꺼진다  → 전부 가능
    //   gemini-3.5-flash-lite 기본이 추론 없음. medium부터 추론이 붙는다       → LOW를 못 고른다
    //
    // ⚠️ flash-lite의 LOW를 뺀 이유는 400이 나서가 아니다 — 200으로 통과하면서 추론이 0이라
    // OFF와 구별되지 않는다. "고를 수 있다"의 기준은 여기서도 실제 효과다(opus-4.7/4.6과 같은 잣대).
    private static final List<Entry> ENTRIES = List.of(
            new Entry("anthropic/claude-opus-4.8", "Claude Opus 4.8",
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("anthropic/claude-opus-4.7", "Claude Opus 4.7", Thinking.none()),
            new Entry("anthropic/claude-opus-4.6", "Claude Opus 4.6", Thinking.none()),
            new Entry("anthropic/claude-haiku-4.5", "Claude Haiku 4.5", Thinking.none()),
            new Entry("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview",
                    Thinking.of(ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("google/gemini-3.5-flash", "Gemini 3.5 Flash",
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite",
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH))
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

    public static Thinking thinkingOf(String slug) {
        return ENTRIES.stream()
                .filter(entry -> entry.id().equals(slug))
                .findFirst()
                .map(Entry::thinking)
                .orElseGet(Thinking::none);
    }

    /**
     * 방이 고른 레벨을 이 모델이 실제로 받을 수 있는 값으로 맞춘다(발송 직전에 쓴다).
     * <p>
     * 저장은 자유롭게 받고 보낼 때 맞추는 이유: 모델과 레벨은 따로 바뀌므로 "HIGH인 방의
     * 모델을 추론 없는 모델로 교체"가 정상 경로다. 그때 400을 던지면 모델을 못 바꾸게 된다.
     * 보정은 카탈로그 소유자인 백엔드 일이다 — ai-server는 받은 값을 그대로 실행한다.
     * <p>
     * 규칙은 하나 — <b>고를 수 없는 값이면 그 모델의 첫 선택지로 내린다.</b> 목록 순서가
     * OFF→HIGH라서 "가장 약한 값"이 되고, 셋이 한 번에 맞는다: 추론을 못 하는 모델은 OFF로,
     * 못 끄는 pro-preview의 OFF는 LOW로, 효과가 없는 flash-lite의 LOW는 OFF로.
     * <p>
     * {@code null}(아직 안 고름)은 OFF로 본다 → 레벨 노출 이전의 거동과 정확히 같다.
     */
    public static ThinkingLevel resolveThinking(String slug, ThinkingLevel level) {
        List<ThinkingLevel> selectable = thinkingOf(slug).selectable();
        ThinkingLevel wanted = (level == null) ? ThinkingLevel.OFF : level;
        return selectable.contains(wanted) ? wanted : selectable.get(0);
    }
}
