package com.soksak.soksak.upload.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationPolicyTest {

    private Map<String, Double> raw(double sexual, double graphic) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("sexual", sexual);
        scores.put("violence/graphic", graphic);
        scores.put("violence", 0.0);
        return scores;
    }

    private boolean blocks(Map<String, Double> raw) {
        return ModerationPolicy.blocks(ModerationScores.from(raw));
    }

    @Test
    @DisplayName("sexual은 0.65부터 막는다")
    void sexual_threshold_is_inclusive() {
        assertThat(blocks(raw(0.6499, 0))).isFalse();
        assertThat(blocks(raw(0.65, 0))).isTrue();
    }

    @Test
    @DisplayName("violence/graphic은 0.9부터 막는다")
    void graphic_threshold_is_inclusive() {
        assertThat(blocks(raw(0, 0.8999))).isFalse();
        assertThat(blocks(raw(0, 0.9))).isTrue();
    }

    @Test
    @DisplayName("violence가 높아도 graphic이 낮으면 통과한다 — 전투·검도 그림")
    void violence_alone_does_not_block() {
        Map<String, Double> battle = raw(0.0001, 0.05);
        battle.put("violence", 0.77);
        assertThat(blocks(battle)).isFalse();
    }

    @Test
    @DisplayName("필요한 점수가 빠지면 0점이 아니라 null — 검사가 조용히 꺼지지 않게")
    void missing_score_is_null() {
        Map<String, Double> noGraphic = new HashMap<>();
        noGraphic.put("sexual", 0.0);
        assertThat(ModerationScores.from(noGraphic)).isNull();
        assertThat(ModerationScores.from(null)).isNull();
    }
}
