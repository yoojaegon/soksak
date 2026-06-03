from __future__ import annotations

from typing import Any

from langchain.messages import AIMessage, AnyMessage, HumanMessage

from app.llm.utils import response_to_text


class ConversationSummarizer:
    _PROMPT = """\
다음은 캐릭터와 유저의 대화 기록입니다.

[기존 요약]
{existing_summary}

[추가 대화]
{turns_text}

위 내용을 합쳐서 두 사람 사이에 있었던 일을 간결하게 요약해줘.
중요한 사건, 감정 변화, 유저가 말한 핵심 정보를 포함해야 해.
3~5문장으로 작성해."""

    def __init__(self, llm: Any) -> None:
        self._llm = llm

    def update(self, existing_summary: str | None, new_turns: list[AnyMessage]) -> str:
        prompt = self._PROMPT.format(
            existing_summary=existing_summary or "없음",
            turns_text=self._turns_to_text(new_turns),
        )
        response = self._llm.invoke([HumanMessage(content=prompt)])
        return response_to_text(response)

    @staticmethod
    def _turns_to_text(turns: list[AnyMessage]) -> str:
        lines = []
        for msg in turns:
            if isinstance(msg, HumanMessage):
                lines.append(f"유저: {msg.content}")
            elif isinstance(msg, AIMessage):
                lines.append(f"캐릭터: {msg.content}")
        return "\n".join(lines)
