package com.soksak.soksak.aiClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방이 고른 추론 레벨 → 실제로 보낼 값. 저장은 자유롭게 받고 여기서 맞추는 구조라,
 * 이 보정이 틀리면 증상이 "요청이 400으로 죽는다"로 나타난다(끌 수 없는 모델에 끔을 보냄).
 */
class ModelCatalogThinkingTest {

    // 추론 토큰이 실제로 나오는 유일한 Claude 모델(2026-09-15 실측). 4.7/4.6은 effort를
    // 받기만 하고 추론이 0이라 카탈로그가 노브를 안 연다.
    private static final String CLAUDE = "anthropic/claude-opus-4.8";
    private static final String PRO_PREVIEW = "google/gemini-3.1-pro-preview";
    private static final String FLASH_LITE = "google/gemini-3.5-flash-lite";
    // 추론을 못 하는 모델 자리. haiku는 effort 파라미터 자체를 400으로 거절한다.
    private static final String NO_THINKING = "anthropic/claude-haiku-4.5";

    @Test
    @DisplayName("고른 레벨은 그대로 간다")
    void keeps_the_chosen_level() {
        assertThat(ModelCatalog.resolveThinking(CLAUDE, ThinkingLevel.HIGH))
                .isEqualTo(ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("아직 안 고른 방(null)은 꺼진 것으로 본다 — 레벨 노출 이전과 같은 거동")
    void null_means_off() {
        assertThat(ModelCatalog.resolveThinking(CLAUDE, null)).isEqualTo(ThinkingLevel.OFF);
    }

    @Test
    @DisplayName("끌 수 없는 모델의 OFF는 바닥 레벨로 내려간다 (끄면 400)")
    void off_becomes_the_lowest_level_when_it_cannot_be_disabled() {
        assertThat(ModelCatalog.resolveThinking(PRO_PREVIEW, ThinkingLevel.OFF))
                .isEqualTo(ThinkingLevel.LOW);
        // 안 고른 방도 같은 길을 탄다 — pro-preview 방은 자동으로 low가 된다.
        assertThat(ModelCatalog.resolveThinking(PRO_PREVIEW, null)).isEqualTo(ThinkingLevel.LOW);
    }

    @Test
    @DisplayName("추론이 없는 모델로 바꾸면 고른 값과 무관하게 OFF")
    void unsupported_model_wins_over_the_saved_choice() {
        // 이게 저장 단계에서 400을 던지지 않는 이유다 — HIGH인 방의 모델을 이걸로 바꾸는 건
        // 정상 경로고, 되돌리면 HIGH가 그대로 살아 있어야 한다.
        assertThat(ModelCatalog.resolveThinking(NO_THINKING, ThinkingLevel.HIGH))
                .isEqualTo(ThinkingLevel.OFF);
    }

    @Test
    @DisplayName("효과가 없는 레벨도 못 고른다 — flash-lite의 LOW는 OFF로 내려간다")
    void ineffective_level_falls_back_too() {
        // 400이 나서가 아니라 200으로 통과하면서 추론이 0이라 OFF와 구별되지 않기 때문이다.
        assertThat(ModelCatalog.resolveThinking(FLASH_LITE, ThinkingLevel.LOW))
                .isEqualTo(ThinkingLevel.OFF);
        assertThat(ModelCatalog.resolveThinking(FLASH_LITE, ThinkingLevel.MEDIUM))
                .isEqualTo(ThinkingLevel.MEDIUM);
    }

    @Test
    @DisplayName("픽커에 회색으로라도 보여줄 전체 어휘는 네 값이다")
    void the_full_vocabulary_is_exposed() {
        assertThat(ModelCatalog.thinkingLevels()).containsExactly(
                ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH);
    }

    @Test
    @DisplayName("카탈로그에 없는 slug은 추론 없음으로 본다")
    void unknown_slug_is_treated_as_unsupported() {
        assertThat(ModelCatalog.thinkingOf("who/knows").supported()).isFalse();
        assertThat(ModelCatalog.resolveThinking("who/knows", ThinkingLevel.HIGH))
                .isEqualTo(ThinkingLevel.OFF);
    }

    @Test
    @DisplayName("전송 값은 소문자다 — ai-server의 프롬프트 모드와 같은 결")
    void wire_format_is_lowercase() {
        assertThat(ThinkingLevel.MEDIUM.wireName()).isEqualTo("medium");
    }
}
