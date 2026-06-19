from __future__ import annotations

from langchain.messages import SystemMessage

from app.prompts.config import PromptConfig
from app.prompts.sections import (
    apply_placeholders,
    character_section,
    lore_section,
    memory_section,
    response_section,
    rules_section,
    user_section,
    writing_section,
)


def build_system_message(
    persona: str,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
    config: PromptConfig | None = None,
    user_name: str | None = None,
    user_persona: str | None = None,
    char_name: str | None = None,
) -> SystemMessage:
    config = config or PromptConfig()

    sections = [
        rules_section(),
        writing_section(),
        response_section(config),
        character_section(persona),
        user_section(user_persona),
        lore_section(lore_entries),
        memory_section(summary),
    ]
    content = "\n\n".join(s for s in sections if s)
    content = apply_placeholders(content, user_name=user_name, char_name=char_name)
    return SystemMessage(content=content)
