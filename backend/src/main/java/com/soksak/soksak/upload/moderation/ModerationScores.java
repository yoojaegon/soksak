package com.soksak.soksak.upload.moderation;

import java.util.Map;

// 차단 판정에 쓰는 점수만 추린 것. 판정(허용/차단)이 아니라 점수를 캐시해 두면
// 기준점(ModerationPolicy)을 바꿔도 캐시를 비울 필요가 없다.
public record ModerationScores(double sexual, double violenceGraphic) {
    static final String SEXUAL = "sexual";
    static final String VIOLENCE_GRAPHIC = "violence/graphic";

    // 키 이름이 바뀌는 등으로 점수가 비면 null — 0으로 치면 검사가 조용히 꺼진다.
    static ModerationScores from(Map<String, Double> scores) {
        if (scores == null) return null;
        Double sexual = scores.get(SEXUAL);
        Double graphic = scores.get(VIOLENCE_GRAPHIC);
        if (sexual == null || graphic == null) return null;
        return new ModerationScores(sexual, graphic);
    }
}
