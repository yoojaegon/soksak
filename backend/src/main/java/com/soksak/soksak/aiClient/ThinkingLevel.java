package com.soksak.soksak.aiClient;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 방마다 고르는 추론(thinking) 깊이. 사용자에게 보이는 추론 레벨은 이 값뿐이다.
 *
 * 제공사 인자는 이름도 값도 다르다. Anthropic effort는 max/xhigh/high/medium/low이고
 * 끄는 값이 없어서 필드를 안 보내는 것으로 끈다. Gemini thinking_level은 minimal/low/medium/high이고
 * 역시 끄는 값이 없어서 thinking_budget=0으로 끈다.
 * 그래서 여기 값은 두 제공사에 모두 있는 값에 OFF를 더한 것이다.
 * 이 값을 제공사 인자로 바꾸는 일은 ai-server의 llm/factory.py가 맡는다.
 *
 * JSON으로는 소문자로 나간다. 프롬프트 모드("rp", "writing")와 맞춘 것이다.
 */
public enum ThinkingLevel {
    OFF, LOW, MEDIUM, HIGH;

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
