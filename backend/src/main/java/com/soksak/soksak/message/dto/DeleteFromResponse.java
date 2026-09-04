package com.soksak.soksak.message.dto;

// 지운 구간을 덮고 있던 요약 조각이 함께 정리됐는지. 경고가 아니라 알림이다 —
// 블롭 시절엔 "지운 대화가 기억에 남아 있을 수 있다"였지만, 조각 모델에선 실제로 지워진다.
public record DeleteFromResponse(
        boolean summaryTrimmed
) {
}
