from __future__ import annotations

from typing import Any, Iterator

from langchain.messages import AIMessage, HumanMessage
from langchain_core.messages.ai import add_usage

from app.llm.cache import to_system_message
from app.llm.usage import log_usage
from app.llm.utils import response_to_text
from app.prompts.builder import build_system_parts
from app.prompts.config import PromptConfig


def _system_message(
    persona: str,
    lore_entries: list[str] | None,
    summary: str | None,
    config: PromptConfig | None,
    user_name: str | None,
    user_persona: str | None,
    char_name: str | None,
    cacheable: bool,
) -> Any:
    # 조각 경계는 prompts가, 그 조각을 어떤 모양으로 쌀지는 llm이 안다. 여기선 둘을 잇기만 한다
    # — 이 모듈은 제공사가 누군지 끝까지 모른다(cacheable 불리언 하나로 충분하다).
    stable, volatile = build_system_parts(
        persona,
        lore_entries=lore_entries,
        summary=summary,
        config=config,
        user_name=user_name,
        user_persona=user_persona,
        char_name=char_name,
    )
    return to_system_message(stable, volatile, cacheable=cacheable)


def _to_messages(recent_messages: list[dict[str, str]]) -> list:
    out = []
    for m in recent_messages:
        role = m.get("role")
        content = m.get("content", "")
        if role == "user":
            out.append(HumanMessage(content=content))
        elif role == "assistant":
            out.append(AIMessage(content=content))
    return out


def chat(
    llm: Any,
    persona: str,
    user_text: str,
    recent_messages: list[dict[str, str]] | None = None,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
    config: PromptConfig | None = None,
    user_name: str | None = None,
    user_persona: str | None = None,
    char_name: str | None = None,
    cacheable: bool = False,
) -> str:
    system_msg = _system_message(
        persona, lore_entries, summary, config, user_name, user_persona, char_name, cacheable
    )
    history = _to_messages(recent_messages or [])
    messages = [system_msg, *history, HumanMessage(content=user_text)]
    response = llm.invoke(messages)
    log_usage(llm, getattr(response, "usage_metadata", None))
    return response_to_text(response)


def chat_stream(
    llm: Any,
    persona: str,
    user_text: str,
    recent_messages: list[dict[str, str]] | None = None,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
    config: PromptConfig | None = None,
    user_name: str | None = None,
    user_persona: str | None = None,
    char_name: str | None = None,
    cacheable: bool = False,
) -> Iterator[str]:
    system_msg = _system_message(
        persona, lore_entries, summary, config, user_name, user_persona, char_name, cacheable
    )
    history = _to_messages(recent_messages or [])
    messages = [system_msg, *history, HumanMessage(content=user_text)]
    # 사용량이 오는 모양은 제공사마다 다르다 — Anthropic은 마지막 청크에 한 번에 몰아주고,
    # Gemini는 청크마다 델타로 준다. 둘 다 받으려면 더해 나가는 수밖에 없다.
    # 빈 청크에 add_usage를 먹이면 0으로 채운 dict가 나와 '안 받았다'와 '0을 받았다'가
    # 구분되지 않으므로, 실제로 온 것만 더한다(아래 finally가 이 차이에 기댄다).
    usage = None
    try:
        for chunk in llm.stream(messages):
            chunk_usage = getattr(chunk, "usage_metadata", None)
            if chunk_usage:
                usage = add_usage(usage, chunk_usage)
            text = response_to_text(chunk)
            if text:
                yield text
    finally:
        # 클라이언트가 중간에 끊어도(GeneratorExit) 그때까지 받은 사용량은 남긴다 — 끊긴 대화가
        # 공짜였던 것처럼 보이면 비용을 추적할 수 없다. 다만 Anthropic처럼 마지막에 몰아주는
        # 제공사는 중도 이탈 시 사용량이 아예 안 온다. 그때 0을 찍으면 '공짜'로 보이는 건
        # 물론이고 캐시읽기=0이 캐싱 고장 신호와 똑같아진다 — 차라리 한 줄도 남기지 않는다.
        log_usage(llm, usage)
