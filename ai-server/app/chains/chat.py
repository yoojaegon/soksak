from __future__ import annotations

from typing import Any, Iterator

from langchain.messages import AIMessage, HumanMessage

from app.llm.utils import response_to_text
from app.prompts.builder import build_system_message


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
) -> str:
    system_msg = build_system_message(persona, lore_entries=lore_entries, summary=summary)
    history = _to_messages(recent_messages or [])
    messages = [system_msg, *history, HumanMessage(content=user_text)]
    response = llm.invoke(messages)
    return response_to_text(response)


def chat_stream(
    llm: Any,
    persona: str,
    user_text: str,
    recent_messages: list[dict[str, str]] | None = None,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
) -> Iterator[str]:
    system_msg = build_system_message(persona, lore_entries=lore_entries, summary=summary)
    history = _to_messages(recent_messages or [])
    messages = [system_msg, *history, HumanMessage(content=user_text)]
    for chunk in llm.stream(messages):
        text = response_to_text(chunk)
        if text:
            yield text
