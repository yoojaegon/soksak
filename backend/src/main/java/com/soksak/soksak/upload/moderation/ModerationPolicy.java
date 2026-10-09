package com.soksak.soksak.upload.moderation;

// OpenAI omni-moderation의 점수를 보고 차단 여부를 정한다.
// 기준점은 실측으로 정했다 — 샘플·점수 분포와 근거는 결정 기록 D17.
// - flagged는 쓰지 않는다: violence 기준이 낮아 창 든 기사 판화·검도 사진까지 걸린다.
// - violence도 쓰지 않는다: 전투화(0.77)와 고어 명화(0.40~)가 겹쳐 가를 수 없다. 픽션 폭력은 허용이 정책이다.
// - 이미지 입력에선 sexual/minors가 판정되지 않는다(텍스트 전용). 성인 수위 자체를 안 받으므로 sexual 전체로 막는다.
public final class ModerationPolicy {
    // 비키니 아머 코스프레(0.78)부터 막힌다. 명화 누드는 0.2대라 통과.
    static final double SEXUAL_THRESHOLD = 0.65;
    // 실사 수준의 상처(0.95~)만 막는다. 피 분장·수술 사진(0.85)과 그림 고어(~0.36)는 통과.
    static final double VIOLENCE_GRAPHIC_THRESHOLD = 0.9;

    private ModerationPolicy() {}

    public static boolean blocks(ModerationScores scores) {
        return scores.sexual() >= SEXUAL_THRESHOLD || scores.violenceGraphic() >= VIOLENCE_GRAPHIC_THRESHOLD;
    }
}
