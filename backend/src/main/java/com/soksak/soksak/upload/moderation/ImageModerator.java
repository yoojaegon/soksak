package com.soksak.soksak.upload.moderation;

// 업로드된 이미지의 점수를 매긴다. 차단 여부는 호출하는 쪽이 ModerationPolicy로 정한다
// (점수를 캐시해 두고 기준점이 바뀌어도 다시 판정할 수 있도록).
// 검사 자체를 못 하면(키 없음·타임아웃·외부 장애·응답에 점수 없음) IMAGE_MODERATION_UNAVAILABLE —
// 통과시키지 않는다(fail-closed).
public interface ImageModerator {
    ModerationScores score(byte[] image, String mimeType);
}
