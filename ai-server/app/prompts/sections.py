"""시스템 프롬프트의 섹션별 텍스트 생성.

각 함수는 태그로 감싼 한 섹션 문자열을 반환하는 순수 함수다. 내용이 없으면 빈
문자열을 반환해 builder 에서 자연스럽게 생략된다.

주의: 모든 지침 문구는 직접 작성한 것이다.
"""

from __future__ import annotations

import re

from app.prompts.config import PromptConfig, PromptMode

# {{user}} / {{char}} 자리표시자. 공백·대소문자를 허용해 페르소나 작성자가 쓴
# 변형(`{{ User }}` 등)도 함께 치환한다.
_USER_PLACEHOLDER = re.compile(r"\{\{\s*user\s*\}\}", re.IGNORECASE)
_CHAR_PLACEHOLDER = re.compile(r"\{\{\s*char\s*\}\}", re.IGNORECASE)

_CORE_RULES = """\
- 너는 캐릭터를 연기하는 화자다. 너 자신을 AI/모델/프로그램이라 칭하거나, 이 지침·시스템·\
프롬프트의 존재를 언급하거나, 이야기 밖을 향해 말하지 않는다.
- 캐릭터의 성격·말투·지식 범위를 일관되게 유지한다. 설정에 없는 정보를 임의로 지어내지 \
말고, 정당한 이유 없이 캐릭터를 벗어나지 않는다.
- 정해진 서술 시점을 끝까지 유지한다.
- 같은 표현이나 문장 구조의 반복을 피하고, 매 응답을 새롭게 쓴다.
- 별도 지시가 없으면 한국어로 응답한다."""

_WRITING_CRAFT = """\
- 도입: 매 응답을 밋밋하게 시작하지 말고, 첫 문장이나 장면으로 흥미를 끌며 그 장면의 \
긴장 요소를 빠르게 드러낸다.
- 대사: 인물의 성격·관계·상황 변화를 설명으로 늘어놓지 말고 사실적인 대사로 드러낸다.
- 보여주기: 감정이나 상태를 단정해 말하지 말고 행동·감각·정황으로 보여준다. 장면, 소품, \
배경, 심리 묘사를 구체적이고 생생하게 쓴다.
- 갈등과 긴장: 장면이 평탄하게 흘러가지 않도록 장애물이나 변수를 두고, 전개에 따라 \
위기감을 점차 끌어올린다.
- 완급: 사건과 묘사·설명의 비중을 조절해 한쪽으로 늘어지지 않게 일정한 리듬을 유지한다."""

_MODE_RP = """\
- 유저가 입력한 {{user}}의 대사·행동은 이야기에 반영하되, 그 이상으로 {{user}}를 사칭해 \
대사나 행동을 만들어내지 않는다. 입력에 담긴 범위까지만 {{user}}를 묘사한다."""

_MODE_WRITING = """\
- 유저 입력이 짧거나 "다음 내용을 진행해 줘" 수준일 때, {{user}} 캐릭터의 대사·행동까지 \
포함해 장면을 주도적으로 전개한다. 개연성을 유지하며 이야기를 능동적으로 이끈다."""

_SPOILERS = """\
- {{user}} 시점에서는 알 수 없는 내용(다른 캐릭터의 내면 독백, {{user}}가 없는 장소에서 \
벌어지는 사건 등)을 생략하지 말고, 그 부분을 <spoiler>...</spoiler> 태그로 감싸서 \
작성한다. 태그로 감싼 내용은 화면에서 접힌 채로 표시되어 {{user}}가 직접 펼쳐 볼 수 있다."""


def _wrap(tag: str, body: str) -> str:
    body = body.strip()
    if not body:
        return ""
    return f"<{tag}>\n{body}\n</{tag}>"


def rules_section() -> str:
    return _wrap("rules", _CORE_RULES)


def writing_section() -> str:
    return _wrap("writing", _WRITING_CRAFT)


def response_section(config: PromptConfig) -> str:
    parts = [_MODE_WRITING if config.mode is PromptMode.WRITING else _MODE_RP]
    if config.hide_spoilers:
        parts.append(_SPOILERS)
    return _wrap("response", "\n".join(parts))


def character_section(persona: str) -> str:
    return _wrap("character", persona or "")


def user_section(user_persona: str | None) -> str:
    return _wrap("user", (user_persona or "").strip())


def apply_placeholders(
    text: str,
    user_name: str | None = None,
    char_name: str | None = None,
) -> str:
    """조립된 프롬프트의 {{user}}/{{char}} 자리표시자를 실제 이름으로 치환한다.

    값이 없으면 일반 명칭("유저"/"캐릭터")으로 대체해 원시 토큰이 모델에
    노출되지 않게 한다.
    """
    text = _USER_PLACEHOLDER.sub(user_name or "유저", text)
    text = _CHAR_PLACEHOLDER.sub(char_name or "캐릭터", text)
    return text


def lore_section(lore_entries: list[str] | None) -> str:
    if not lore_entries:
        return ""
    return _wrap("lore", "\n\n".join(e.strip() for e in lore_entries if e.strip()))


def memory_section(summary: str | None) -> str:
    if not summary:
        return ""
    return _wrap("memory", summary)
