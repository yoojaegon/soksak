"""프롬프트 캐싱 — 조각 경계와 블록 모양.

캐싱은 **프리픽스 일치**라서, 여기서 지키는 건 "안정 구간의 바이트가 턴마다 같은가" 하나다.
실제 적중은 API를 불러야 알 수 있고(usage 로그의 `캐시읽기`), 여기선 그 앞 단계를 막는다.
"""

from __future__ import annotations

from app.llm.cache import to_system_message
from app.llm.factory import supports_prompt_cache
from app.prompts.builder import build_system_parts

_ROOM = dict(persona="세린", user_persona="20대 남성", char_name="세린", user_name="유저")


# --- 조각 경계 --------------------------------------------------------------

def test_lore_is_the_only_volatile_section():
    stable, volatile = build_system_parts(**_ROOM, summary="어제 처음 만났다.",
                                          lore_entries=["세계관: 학원물"])
    assert "<lore>" in volatile and "학원물" in volatile
    # 나머지는 전부 안정 구간에 — 특히 <memory>가 여기 있어야 캐시 구간에 들어간다.
    for tag in ("<rules>", "<writing>", "<response>", "<character>", "<user>", "<memory>"):
        assert tag in stable, tag
    assert "<lore>" not in stable


def test_stable_part_is_byte_identical_when_only_lore_changes():
    """캐싱의 전제. 로어만 바뀐 두 턴의 안정 구간이 다르면 매 턴 캐시가 깨진다."""
    turn1, _ = build_system_parts(**_ROOM, summary="어제 처음 만났다.",
                                  lore_entries=["세계관: 학원물"])
    turn2, _ = build_system_parts(**_ROOM, summary="어제 처음 만났다.",
                                  lore_entries=["인물: 담임 선생님", "장소: 옥상"])
    assert turn1 == turn2


def test_stable_part_changes_when_summary_changes():
    """반대 방향 — 기억이 갱신되면 안정 구간도 바뀐다(그 턴은 캐시를 다시 쓴다)."""
    a, _ = build_system_parts(**_ROOM, summary="어제 처음 만났다.")
    b, _ = build_system_parts(**_ROOM, summary="어제 처음 만났고, 오늘 다시 봤다.")
    assert a != b


def test_placeholders_applied_to_both_parts():
    """치환을 조각마다 돌리는 걸 빠뜨리면 로어 안 {{char}}가 그대로 나간다."""
    stable, volatile = build_system_parts(
        persona="{{char}}는 조용하다", user_persona="{{user}}는 학생",
        lore_entries=["{{char}}의 고향은 {{user}}와 같다"],
        char_name="세린", user_name="지훈",
    )
    assert "{{" not in stable and "{{" not in volatile
    assert "세린" in volatile and "지훈" in volatile


# --- 블록 모양 --------------------------------------------------------------

def test_not_cacheable_is_one_plain_string():
    msg = to_system_message("안정", "변동", cacheable=False)
    assert msg.content == "안정\n\n변동"  # 캐싱 켜기 전과 바이트가 같아야 한다


def test_cacheable_marks_only_the_stable_block():
    msg = to_system_message("안정", "변동", cacheable=True)
    assert msg.content == [
        {"type": "text", "text": "안정", "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": "변동"},
    ]


def test_empty_volatile_keeps_the_marker_in_place():
    """로어 없는 턴이라고 표시가 다른 자리로 옮겨가면 다음 턴에 캐시가 깨진다."""
    msg = to_system_message("안정", "", cacheable=True)
    assert msg.content == [
        {"type": "text", "text": "안정", "cache_control": {"type": "ephemeral"}},
    ]
    # 로어가 붙어도 앞 블록은 그대로다.
    assert to_system_message("안정", "변동", cacheable=True).content[0] == msg.content[0]


def test_join_matches_between_cacheable_and_not():
    """캐싱 on/off로 프롬프트 '내용'이 달라지면 안 된다 — 모양만 다르다."""
    stable, volatile = build_system_parts(**_ROOM, summary="요약", lore_entries=["로어"])
    plain = to_system_message(stable, volatile, cacheable=False).content
    blocks = to_system_message(stable, volatile, cacheable=True).content
    assert plain == "\n\n".join(b["text"] for b in blocks)


# --- 제공사 능력 ------------------------------------------------------------

def test_only_anthropic_needs_explicit_cache():
    assert supports_prompt_cache("anthropic/claude-opus-4.8") is True
    assert supports_prompt_cache("anthropic/claude-haiku-4.5") is True
    # Gemini는 암시적 캐싱이라 켤 게 없다 — False는 "못 한다"가 아니다.
    assert supports_prompt_cache("google/gemini-3.5-flash-lite") is False
