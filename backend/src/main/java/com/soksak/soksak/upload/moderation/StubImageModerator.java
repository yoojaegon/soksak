package com.soksak.soksak.upload.moderation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")   // 테스트에서는 OpenAI 없이 전부 통과시킨다. 차단 기준은 ModerationPolicyTest가 맡는다.
public class StubImageModerator implements ImageModerator {
    @Override
    public ModerationScores score(byte[] image, String mimeType) {
        return new ModerationScores(0, 0);
    }
}
