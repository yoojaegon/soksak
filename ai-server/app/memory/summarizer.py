"""대화 구간 요약.

한 번에 한 구간만 요약한다. 이전 요약을 다시 써서 갱신하지 않는다 — 갱신형은 매 요약마다
과거 전체가 모델을 한 번 더 통과해서, 세션이 길어질수록 초반 사실이 조용히 마모된다.
누적은 백엔드가 요약을 여러 건 쌓아 두는 방식으로 처리하고, 여기서는 넘겨받은 구간
하나를 독립적인 기록으로 만든다.

[앞선 기록]은 대명사·생략된 주어를 해석하기 위한 맥락일 뿐이며 출력에 포함하지 않는다.

⚠️ **고정 슬롯 명세(_FORMAT)는 프롬프트와 `summary` 필드 설명 양쪽에 둔다.** 프롬프트에만
두면 모델이 형식을 통째로 버리고 줄글 한 단락으로 답하는 일이 생긴다 — 🔬 2026-09-14 실측
(claude-haiku-4.5, 같은 구간 반복): [앞선 기록]이 1건일 때 형식 붕괴 **6/12**. 구조화 출력에선
필드 설명이 프롬프트보다 세게 먹어서, 같은 명세를 필드 설명에 얹은 것만으로 **0/24** 가 됐다.
(2건일 때 0/9, 없을 때 0/4 — 하필 1건인 경우가 방마다 꼭 한 번 온다. SummaryPlan 참고.)

주의: 모든 지침 문구는 직접 작성한 것이다(docs/prompt-architecture.md §0).
"""

from __future__ import annotations

import logging
from typing import Any

from langchain.messages import AIMessage, AnyMessage, HumanMessage
from pydantic import BaseModel, Field

from app.llm.utils import response_to_text

logger = logging.getLogger(__name__)

# 중요도를 매기지 못했을 때의 값. 대부분의 구간이 2~3이라 중간값을 둔다.
_DEFAULT_IMPORTANCE = 3
_MIN_IMPORTANCE = 1
_MAX_IMPORTANCE = 5
# 검색어가 너무 많으면 변별력이 없다. 넘치면 앞에서부터 자른다.
_MAX_KEYWORDS = 10

_FORMAT = """\
시점·장소: (언제 어디서인지 한 문장. 상대적인 표현도 괜찮다 — "감금 사흘째" 등)
상황: (시간·장소 말고, 이 구간에 깔려 있는 전제와 인물들의 구도)
사건:
- (일어난 순서대로, 최대 6개. 중요한 것만 고른다)
인물:
- 이름: (감정·태도의 변화와 그 계기, 관계의 변화)
대사:
- 이름: "대사" (이야기를 움직인 말만, 최대 6개. 없으면 "없음")
미해결: (약속, 예고, 아직 풀리지 않은 의문. 없으면 "없음")"""

# 같은 명세를 필드 설명에도 싣는다(위 docstring의 실측). 문구를 프롬프트와 갈라 두면
# 한쪽만 고쳐졌을 때 형식이 다시 흔들리므로 반드시 _FORMAT 하나에서 파생시킬 것.
_SUMMARY_FIELD = f"""\
정해진 형식을 그대로 채운 요약 본문. 머리말은 바꾸지 말고 내용만 채운다.

{_FORMAT}"""


class SummaryResult(BaseModel):
    """요약 한 건. summary 는 위 형식을 채운 본문, 나머지는 검색·선택용 메타데이터."""

    summary: str = Field(description=_SUMMARY_FIELD)
    importance: int = Field(
        default=_DEFAULT_IMPORTANCE,
        description="이 구간이 이야기 전체에서 갖는 무게. 1에서 5 사이의 정수.",
    )
    keywords: list[str] = Field(
        default_factory=list,
        description="나중에 이 기록을 다시 찾을 때 쓸 검색어 3~8개.",
    )


_ROLE_RULES = """\
너는 진행 중인 창작 대화를 나중에 다시 찾아볼 수 있게 남기는 기록자다. 아래 [이번 구간]을 \
정해진 형식에 맞춰 기록한다."""

_WRITING_RULES = """\
- 일어난 일을 그대로 적는다. 내용을 평가하거나 순화하지 말고, 폭력적이거나 선정적인 장면도 \
사실대로 기록한다. 이것은 창작물의 보존용 기록이다.
- 객관적인 3인칭 과거형으로 쓴다. 인물의 말투를 흉내 내거나 1인칭으로 쓰지 않는다.
- [이번 구간]에 실제로 나온 내용만 쓴다. 빈칸을 그럴듯한 추측으로 채우지 않는다.
- 누가 한 말이고 누가 한 행동인지 인물을 혼동하지 않는다. 등장인물은 둘보다 많을 수 있다.
- 원문의 언어를 유지하고 번역하지 않는다. 고유명사는 원문 표기 그대로 쓴다.
- 항목의 머리말은 그대로 두고 내용만 채운다. 단서가 없는 항목에는 "불명"이라고 적는다."""

_IMPORTANCE_RUBRIC = """\
importance 는 이 구간이 이야기 전체에서 갖는 무게다.
- 5: 반전, 죽음, 핵심 진실의 공개
- 4: 관계의 결정적 전환, 각성, 큰 충돌
- 3: 새로운 인물·장소·물건의 등장
- 2: 감정의 변화, 의미 있는 대화
- 1: 일상, 분위기
대부분의 구간은 2 또는 3이다. 4 이상은 이야기의 방향이 실제로 바뀐 경우에만 준다."""

_KEYWORDS_RULES = """\
keywords 는 나중에 이 기록을 다시 찾을 때 쓸 검색어다. 인물 이름, 지명, 사물, 사건, \
설정 용어를 3~8개 고른다. 어느 대화에나 나오는 흔한 낱말은 넣지 않는다."""

_PROMPT = """\
{role_rules}

[앞선 기록]
{previous}

[이번 구간]
{segment}

작성 규칙:
{writing_rules}

형식:
{fmt}

{importance_rubric}

{keywords_rules}"""

_NO_PREVIOUS = "없음 (이 구간이 이야기의 시작이다)"

_PREVIOUS_NOTE = """\
(위는 이번 구간 앞에 있었던 일이다. 대명사나 생략된 주어를 해석하는 데만 쓰고, \
여기 적힌 내용을 다시 옮겨 적지 않는다.)"""


class ConversationSummarizer:
    def __init__(self, llm: Any) -> None:
        self._llm = llm

    def summarize(
        self,
        previous_summaries: list[str] | None,
        new_turns: list[AnyMessage],
    ) -> SummaryResult:
        prompt = self._build_prompt(previous_summaries, new_turns)
        message = HumanMessage(content=prompt)

        try:
            result = self._llm.with_structured_output(SummaryResult).invoke([message])
            if isinstance(result, SummaryResult) and result.summary.strip():
                return self._normalize(result)
            logger.warning("구조화 출력이 비어 있어 평문 요약으로 대체한다.")
        except Exception:
            # 요약 모델은 SUMMARY_MODEL로 바뀔 수 있고, 구조화 출력의 구현은 제공사마다 다르다
            # (제공사 OpenAI 호환 엔드포인트를 쓰던 시절엔 Anthropic이 response_format을
            # 무시해서 이 경로로만 떨어졌다). 메타데이터를 잃더라도 요약 자체는 살린다.
            logger.warning("구조화 출력 실패 — 평문 요약으로 대체한다.", exc_info=True)

        text = response_to_text(self._llm.invoke([message])).strip()
        return SummaryResult(summary=text, importance=_DEFAULT_IMPORTANCE, keywords=[])

    def _build_prompt(
        self,
        previous_summaries: list[str] | None,
        new_turns: list[AnyMessage],
    ) -> str:
        return _PROMPT.format(
            role_rules=_ROLE_RULES,
            previous=self._previous_to_text(previous_summaries),
            segment=self._turns_to_text(new_turns),
            writing_rules=_WRITING_RULES,
            fmt=_FORMAT,
            importance_rubric=_IMPORTANCE_RUBRIC,
            keywords_rules=_KEYWORDS_RULES,
        )

    @staticmethod
    def _normalize(result: SummaryResult) -> SummaryResult:
        """모델이 범위를 벗어난 값을 줘도 그대로 저장되지 않게 다듬는다."""
        importance = result.importance
        if not isinstance(importance, int):
            importance = _DEFAULT_IMPORTANCE
        importance = max(_MIN_IMPORTANCE, min(_MAX_IMPORTANCE, importance))

        seen: set[str] = set()
        keywords: list[str] = []
        for keyword in result.keywords:
            cleaned = keyword.strip()
            if not cleaned or cleaned in seen:
                continue
            seen.add(cleaned)
            keywords.append(cleaned)

        return SummaryResult(
            summary=result.summary.strip(),
            importance=importance,
            keywords=keywords[:_MAX_KEYWORDS],
        )

    @staticmethod
    def _previous_to_text(previous_summaries: list[str] | None) -> str:
        entries = [s.strip() for s in (previous_summaries or []) if s and s.strip()]
        if not entries:
            return _NO_PREVIOUS
        return "\n\n".join(entries) + "\n\n" + _PREVIOUS_NOTE

    @staticmethod
    def _turns_to_text(turns: list[AnyMessage]) -> str:
        lines = []
        for msg in turns:
            if isinstance(msg, HumanMessage):
                lines.append(f"유저: {msg.content}")
            elif isinstance(msg, AIMessage):
                lines.append(f"캐릭터: {msg.content}")
        return "\n".join(lines)
