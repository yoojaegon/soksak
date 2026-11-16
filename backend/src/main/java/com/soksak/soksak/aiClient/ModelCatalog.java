package com.soksak.soksak.aiClient;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 채팅 모델 카탈로그. 선택지, 표시명, 기본값의 단일 출처다.
 *
 * slug는 소삭 내부 식별자이고 chat_room.model에 이 값이 저장된다. 그래서 바꾸려면 마이그레이션이 필요하다.
 * 제공사의 실제 모델 ID로 바꾸는 일은 ai-server의 llm/factory.py가 한다.
 * 예를 들어 anthropic/claude-opus-4.8은 claude-opus-4-8이 된다.
 */
@Slf4j
public final class ModelCatalog {

    /**
     * @param cost 이 모델로 한 번 주고받을 때 깎이는 마디.
     *             실제 단가 차이는 이보다 훨씬 크지만, 설명하기 쉽게 1/2/3으로 묶는다.
     *             사용자가 모델을 고를 때 보는 값이라 Thinking과 함께 여기 둔다.
     */
    public record Entry(String id, String label, int cost, Thinking thinking) {}

    /**
     * 이 모델이 추론에 대해 할 수 있는 것. 픽커가 노브를 켤지, 어떤 값을 보여줄지 이걸로 정하므로
     * 프론트에 사본을 두지 않는다.
     *
     * 여기에는 사용자가 고르는 능력만 둔다. 샘플링 미지원이나 프롬프트 캐싱처럼 제공사 사정에
     * 속하는 것은 ai-server가 갖는다. 여기 올리면 ai-server가 매 요청 그 값을 백엔드에서 받아야 한다.
     */
    public record Thinking(boolean supported, List<ThinkingLevel> selectable) {
        // 두 제공사에 모두 있는 값만 둔다. Anthropic에는 xhigh/max가, Gemini에는 minimal이 더 있지만
        // 한쪽에만 있는 값을 열면 모델을 바꿀 때마다 방 설정이 무효가 된다.
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

    /** 픽커가 회색으로라도 보여줄 값 전체. 못 고르는 값을 숨기면 왜 없는지 알 수 없다. */
    public static List<ThinkingLevel> thinkingLevels() {
        return Thinking.ALL;
    }

    // 두 제공사 키로 실제 호출해서 응답을 확인한 모델만 올린다. 순서가 곧 픽커에 보이는 순서다.
    // openai/*는 키가 없어서, google/gemini-2.5-pro는 신규 사용자에게 막혀 있어서 뺐다.
    //
    // 고를 수 있는 추론 레벨은 파라미터를 받아주는지가 아니라 추론 토큰이 실제로 나오는지로 정한다.
    // 받아주기만 하고 효과가 없는 값을 열어 두면 사용자에게 아무 일도 안 하는 설정을 보여주게 된다.
    // 그래서 opus-4.7/4.6과 haiku-4.5는 노브를 열지 않고, pro-preview는 끌 수 없어 OFF를 빼고,
    // flash-lite는 LOW가 OFF와 차이가 없어 LOW를 뺀다. 모델별 측정값은 docs/measurements.md에 있다.
    private static final List<Entry> ENTRIES = List.of(
            new Entry("anthropic/claude-opus-4.8", "Claude Opus 4.8", 3,
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("anthropic/claude-opus-4.7", "Claude Opus 4.7", 3, Thinking.none()),
            new Entry("anthropic/claude-opus-4.6", "Claude Opus 4.6", 3, Thinking.none()),
            new Entry("anthropic/claude-haiku-4.5", "Claude Haiku 4.5", 1, Thinking.none()),
            new Entry("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", 2,
                    Thinking.of(ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("google/gemini-3.5-flash", "Gemini 3.5 Flash", 1,
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH)),
            new Entry("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1,
                    Thinking.of(ThinkingLevel.OFF, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH))
    );

    // 기본 모델. ai-server의 CHAT_ 프로필 기본값(main.py)도 같은 slug로 맞춰 둔다.
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

    /** 실제로 보낼 slug. null이나 카탈로그에 없는 값은 기본 모델로 바꾸고, 없는 값이면 경고를 남긴다. */
    public static String resolve(String slug){
        if (slug == null) return DEFAULT_ID;
        if (!contains(slug)) {
            log.warn("카탈로그에 없는 모델이라 기본값으로 폴백: model={}", slug);
            return DEFAULT_ID;
        }
        return slug;
    }

    /**
     * resolve()와 같은 값을 경고 없이 돌려준다. 응답에 실제로 쓰이는 모델을 보여줄 때 쓴다.
     * 조회할 때마다 경고가 쌓이지 않게 하려는 것이고, 경고는 보내는 경로의 resolve()가 한 번 남긴다.
     */
    public static String displayed(String slug) {
        return (slug != null && contains(slug)) ? slug : DEFAULT_ID;
    }

    /**
     * 이 모델로 한 턴에 쓸 마디. 방은 ChatRoom.effectiveModel()을 넘긴다.
     * null이나 카탈로그에 없는 slug는 기본 모델 값으로 본다. 실제로 보내는 모델도 resolve() 결과라 둘이 맞아야 한다.
     * resolve()를 부르지 않는 건 경고 로그가 두 번 남지 않게 하려는 것이다.
     *
     * 기본 모델이 카탈로그에 없으면 예외를 던진다. DEFAULT_ID를 빼먹은 실수라서,
     * 조용히 넘어가면 모든 모델이 엉뚱한 마디로 계산돼도 알아채기 어렵다.
     */
    public static int costOf(String slug) {
        return ENTRIES.stream()
                .filter(entry -> entry.id().equals(slug))
                .findFirst()
                .or(() -> ENTRIES.stream().filter(entry -> entry.id().equals(DEFAULT_ID)).findFirst())
                .map(Entry::cost)
                .orElseThrow(() -> new IllegalStateException(
                        "기본 모델이 카탈로그에 없다: " + DEFAULT_ID));
    }

    /** 이 모델의 추론 능력. 카탈로그에 없는 slug는 추론을 못 하는 모델로 본다. */
    public static Thinking thinkingOf(String slug) {
        return ENTRIES.stream()
                .filter(entry -> entry.id().equals(slug))
                .findFirst()
                .map(Entry::thinking)
                .orElseGet(Thinking::none);
    }

    /**
     * 방이 고른 레벨을 이 모델이 받을 수 있는 값으로 맞춘다. 보내기 직전에 쓴다.
     *
     * 저장할 때는 아무 값이나 받고 보낼 때 맞춘다. 모델과 레벨은 따로 바뀌어서,
     * HIGH로 둔 방의 모델을 추론 없는 모델로 바꾸는 것도 정상적인 사용이기 때문이다.
     * 보정은 카탈로그를 가진 백엔드가 하고, ai-server는 받은 값을 그대로 쓴다.
     *
     * 고를 수 없는 값이면 그 모델의 첫 선택지로 내린다. 목록이 OFF부터 HIGH 순이라 가장 약한 값이 된다.
     * 추론 없는 모델은 OFF로, 끌 수 없는 pro-preview의 OFF는 LOW로, flash-lite의 LOW는 OFF로 바뀐다.
     * null(아직 안 고름)은 OFF로 본다.
     */
    public static ThinkingLevel resolveThinking(String slug, ThinkingLevel level) {
        List<ThinkingLevel> selectable = thinkingOf(slug).selectable();
        ThinkingLevel wanted = (level == null) ? ThinkingLevel.OFF : level;
        return selectable.contains(wanted) ? wanted : selectable.get(0);
    }
}
