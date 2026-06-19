"""시스템 프롬프트 조립·치환 단위 테스트.

LLM/네트워크 없이 build_system_message 의 출력 문자열만 검증한다.
실행: `uv run pytest`
"""

from __future__ import annotations

from app.prompts.builder import build_system_message
from app.prompts.config import PromptConfig, PromptMode
from app.prompts.sections import apply_placeholders, user_section


def _build(**kwargs) -> str:
    return build_system_message(**kwargs).content


# --- 섹션 구성/순서 ----------------------------------------------------------

def test_always_on_sections_present():
    content = _build(persona="세린")
    assert "<rules>" in content
    assert "<writing>" in content
    assert "<response>" in content
    assert "<character>" in content


def test_section_order():
    content = _build(
        persona="세린",
        user_persona="20대 남성",
        lore_entries=["세계관: 학원물"],
        summary="둘은 어제 처음 만났다.",
    )
    order = ["<rules>", "<writing>", "<response>", "<character>", "<user>", "<lore>", "<memory>"]
    positions = [content.index(tag) for tag in order]
    assert positions == sorted(positions)


# --- 모드 토글 --------------------------------------------------------------

def test_default_mode_is_rp():
    content = _build(persona="세린")
    assert "사칭" in content  # rp: {{user}}를 사칭하지 않는다
    assert "주도적으로 전개" not in content


def test_writing_mode():
    content = _build(persona="세린", config=PromptConfig(mode=PromptMode.WRITING))
    assert "주도적으로 전개" in content


# --- 스포일러 토글 ----------------------------------------------------------

def test_spoiler_off_by_default():
    assert "<spoiler>" not in _build(persona="세린")


def test_spoiler_on():
    content = _build(persona="세린", config=PromptConfig(fold_spoilers=True))
    assert "<spoiler>" in content


# --- 자리표시자 치환 --------------------------------------------------------

def test_placeholder_substitution():
    content = _build(
        persona="{{char}}는 {{user}}를 살핀다.",
        user_name="도윤",
        char_name="세린",
    )
    assert "도윤" in content
    assert "세린" in content
    assert "{{user}}" not in content
    assert "{{char}}" not in content


def test_placeholder_fallback_when_names_missing():
    content = _build(persona="{{char}}가 {{user}}에게 말한다.")
    assert "유저" in content
    assert "캐릭터" in content
    # 원시 토큰이 모델에 노출되면 안 됨
    assert "{{" not in content
    assert "}}" not in content


def test_placeholder_handles_spacing_and_case():
    assert apply_placeholders("{{ User }}", user_name="도윤") == "도윤"
    assert apply_placeholders("{{CHAR}}", char_name="세린") == "세린"


# --- 선택 섹션 생략 ---------------------------------------------------------

def test_user_section_omitted_without_persona():
    assert "<user>" not in _build(persona="세린")


def test_user_section_present_with_persona():
    assert "<user>" in _build(persona="세린", user_persona="20대 남성")


def test_user_section_blank_is_empty():
    assert user_section(None) == ""
    assert user_section("   ") == ""


def test_lore_and_memory_omitted_when_absent():
    content = _build(persona="세린")
    assert "<lore>" not in content
    assert "<memory>" not in content


def test_lore_and_memory_present_when_given():
    content = _build(
        persona="세린",
        lore_entries=["세계관: 학원물"],
        summary="둘은 어제 처음 만났다.",
    )
    assert "<lore>" in content and "학원물" in content
    assert "<memory>" in content and "어제 처음" in content


# --- 하위호환 --------------------------------------------------------------

def test_minimal_call_backward_compatible():
    # persona 만으로 호출해도 동작 (config/이름/페르소나 전부 기본값)
    content = _build(persona="세린")
    assert content
    assert "세린" in content
