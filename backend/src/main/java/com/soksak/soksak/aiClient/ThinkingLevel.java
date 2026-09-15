package com.soksak.soksak.aiClient;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 방마다 고르는 추론(thinking) 깊이. 사용자에게 보이는 유일한 어휘다.
 * <p>
 * 제공사 인자는 이름도 값 집합도 다르다(2026-09-15 SDK 확인) — Anthropic {@code effort}는
 * max/xhigh/high/medium/low에 "끔"이 없어 <b>필드를 안 보내는 것</b>이 끔이고, Gemini
 * {@code thinking_level}은 minimal/low/medium/high에 역시 "끔"이 없어 {@code thinking_budget=0}
 * 으로만 끈다. 그래서 여기 있는 값은 <b>두 제공사의 교집합 + OFF</b>이고, 이 값을 제공사
 * 인자로 옮기는 일은 ai-server {@code llm/factory.py}가 혼자 안다(모델 slug 처리와 같은 경계).
 * <p>
 * 직렬화는 소문자다 — 프롬프트 모드("rp"/"writing")와 같은 결로 맞췄다.
 */
public enum ThinkingLevel {
    OFF, LOW, MEDIUM, HIGH;

    @JsonValue
    public String wireName() {
        return name().toLowerCase();
    }
}
