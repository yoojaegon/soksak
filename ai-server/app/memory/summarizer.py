from __future__ import annotations

from typing import Any

from langchain.messages import AIMessage, AnyMessage, HumanMessage

from app.llm.utils import response_to_text


class ConversationSummarizer:
    _PROMPT = """\
아래는 진행 중인 대화의 누적 기록입니다. [기존 요약]을 갱신해 새로운 요약을 만드세요.

[기존 요약]
{existing_summary}

[추가 대화]
{turns_text}

규칙:
- [기존 요약]에 이미 확정된 사실은 유지하고, [추가 대화]의 내용만 그 위에 통합한다. 기존 사실을 임의로 빼거나 뭉뚱그리지 않는다.
- 중요한 사건, 인물들 간 관계와 감정 변화, 등장인물·세계관 설정, 약속이나 미해결 떡밥, 시점·장소를 포함한다.
- 누가 한 말·행동인지 인물을 헷갈리지 말고, 객관적인 3인칭 기록체로 작성한다. 인물의 대사체나 1인칭으로 쓰지 않는다.
- 대화 참여 인원을 둘로 가정하지 않는다(여러 등장인물이 있을 수 있다).
- 간결하게 쓰되, 확정 사실을 누락하지 않는 선에서 분량을 조절한다.
- 다른 말 없이 요약문만 출력한다."""

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
