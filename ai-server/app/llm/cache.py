"""프롬프트 캐싱 — 시스템 프롬프트를 어떤 모양으로 쌀지.

`cache_control` 은 **블록 하나에 붙는 필드**다. "이 문자열의 앞 80%까지 캐시"라는 문법은
없어서, 자르는 지점이 진짜 블록 경계여야 한다. 그래서 시스템 프롬프트를 두 조각으로 받아
앞 조각에만 표시를 찍는다.

이 파일이 `app/llm/` 에 있는 이유: `cache_control` 은 Anthropic 어휘라 Gemini 방에 그대로
보내면 안 된다. 제공사 차이는 factory 밖으로 새지 않게 한다는 규칙에 따라, 이 모양을 아는
코드도 여기 둔다 — `prompts/builder.py` 는 경계만 알고 제공사는 끝까지 모른다.

⚠️ 캐시되는 건 breakpoint **앞**이다. 최소 토큰 미달이면 에러 없이 그냥 캐시가 안 된다
(모델별 512~4096, 세대순이 아님 — docs/llm-config.md). 도는지 확인하는 건 usage 로그의
`캐시읽기` 하나뿐이다.
"""

from __future__ import annotations

from langchain.messages import SystemMessage

# 5분 TTL(기본). 1시간짜리는 쓰기가 2배라, 실제 턴 간격을 usage 로그로 보고 나서 바꿀 것.
_EPHEMERAL = {"type": "ephemeral"}


def to_system_message(stable: str, volatile: str, *, cacheable: bool) -> SystemMessage:
    """(안정 구간, 매 턴 바뀌는 나머지) → SystemMessage.

    `cacheable=False` 면 예전과 똑같이 한 덩어리 문자열이다 — 두 조각을 `\\n\\n` 으로 다시
    붙이므로 캐싱을 켜기 전과 바이트가 같다(프롬프트가 조용히 바뀌지 않는다).
    """
    if not cacheable:
        return SystemMessage(content="\n\n".join(p for p in (stable, volatile) if p))

    blocks: list[dict] = [
        {"type": "text", "text": stable, "cache_control": dict(_EPHEMERAL)}
    ]
    # 로어가 없는 턴은 블록이 하나뿐이다. 그래도 표시는 같은 자리(안정 구간 끝)에 남으므로
    # 다음 턴에 로어가 붙어도 앞 블록의 바이트는 그대로고 캐시가 살아 있다.
    if volatile:
        blocks.append({"type": "text", "text": volatile})
    return SystemMessage(content=blocks)
