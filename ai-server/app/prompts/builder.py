from __future__ import annotations

from langchain.messages import SystemMessage


def build_system_message(
    persona: str,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
) -> SystemMessage:
    parts = [persona]

    if lore_entries:
        parts.append("\n\n[참고 설정]\n" + "\n\n".join(lore_entries))

    if summary:
        parts.append(f"\n\n[이전 대화 요약]\n{summary}")

    return SystemMessage(content="".join(parts))
