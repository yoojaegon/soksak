"""구간 요약기 단위 테스트.

LLM/네트워크 없이 프롬프트 문자열과 결과 정규화만 검증한다.
실행: `uv run pytest`
"""

from __future__ import annotations

from langchain.messages import AIMessage, HumanMessage

from app.memory import token_counter
from app.memory.summarizer import ConversationSummarizer, SummaryResult
from app.memory.token_counter import count_tokens, estimate_tokens

TURNS = [
    HumanMessage(content="어제 도서관에서 본 거, 설명해줄 수 있어?"),
    AIMessage(content="지호는 창밖으로 시선을 돌렸다. \"…들으면 후회할 텐데.\""),
]


class _StructuredStub:
    """with_structured_output(...) 이 돌려주는 러너 대역."""

    def __init__(self, parent: "_LLMStub", result):
        self._parent = parent
        self._result = result

    def invoke(self, messages):
        self._parent.prompts.append(messages[0].content)
        if isinstance(self._result, Exception):
            raise self._result
        return self._result


class _LLMStub:
    """구조화 출력 성공/실패를 모두 흉내 내는 LLM 대역."""

    def __init__(self, structured=None, plain: str = "평문 요약"):
        self._structured = structured
        self._plain = plain
        self.prompts: list[str] = []

    def with_structured_output(self, schema):
        if isinstance(self._structured, Exception):
            raise self._structured
        return _StructuredStub(self, self._structured)

    def invoke(self, messages):
        self.prompts.append(messages[0].content)
        return AIMessage(content=self._plain)


def _summarize(llm, previous=None, turns=None) -> SummaryResult:
    return ConversationSummarizer(llm).summarize(
        previous_summaries=previous,
        new_turns=turns if turns is not None else TURNS,
    )


# --- 프롬프트 구성 ----------------------------------------------------------

def test_prompt_contains_format_slots():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm)
    prompt = llm.prompts[0]
    for slot in ("시점·장소:", "상황:", "사건:", "인물:", "대사:", "미해결:"):
        assert slot in prompt


def test_prompt_contains_segment_turns():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm)
    prompt = llm.prompts[0]
    assert "유저: 어제 도서관에서 본 거, 설명해줄 수 있어?" in prompt
    assert "캐릭터: 지호는 창밖으로" in prompt


def test_prompt_marks_absent_previous_summaries():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm, previous=None)
    assert "없음 (이 구간이 이야기의 시작이다)" in llm.prompts[0]


def test_previous_summaries_are_context_only():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm, previous=["둘은 지난주에 처음 만났다."])
    prompt = llm.prompts[0]
    assert "둘은 지난주에 처음 만났다." in prompt
    # 맥락 파악용일 뿐 다시 옮겨 적지 말라는 지시가 함께 붙어야 한다
    assert "다시 옮겨 적지 않는다" in prompt


def test_blank_previous_summaries_are_dropped():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm, previous=["  ", ""])
    assert "없음 (이 구간이 이야기의 시작이다)" in llm.prompts[0]


def test_importance_rubric_warns_against_high_scores():
    llm = _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[]))
    _summarize(llm)
    # 경고 문구가 없으면 모델이 대부분 4~5를 매겨 점수가 무의미해진다
    assert "대부분의 구간은 2 또는 3이다" in llm.prompts[0]


# --- 결과 정규화 ------------------------------------------------------------

def test_importance_is_clamped_to_range():
    high = _summarize(_LLMStub(SummaryResult(summary="요약", importance=9, keywords=[])))
    low = _summarize(_LLMStub(SummaryResult(summary="요약", importance=0, keywords=[])))
    assert high.importance == 5
    assert low.importance == 1


def test_keywords_are_deduped_and_trimmed():
    result = _summarize(
        _LLMStub(SummaryResult(summary="요약", importance=3, keywords=[" 도서관 ", "도서관", "", "지호"]))
    )
    assert result.keywords == ["도서관", "지호"]


def test_keywords_are_capped():
    many = [f"키워드{i}" for i in range(20)]
    result = _summarize(_LLMStub(SummaryResult(summary="요약", importance=3, keywords=many)))
    assert len(result.keywords) == 10


def test_summary_is_stripped():
    result = _summarize(_LLMStub(SummaryResult(summary="  요약  ", importance=3, keywords=[])))
    assert result.summary == "요약"


# --- 구조화 출력 실패 시 폴백 ------------------------------------------------

def test_falls_back_to_plain_text_when_structured_output_raises():
    llm = _LLMStub(structured=RuntimeError("function calling 미지원"), plain="폴백 요약")
    result = _summarize(llm)
    assert result.summary == "폴백 요약"
    assert result.importance == 3
    assert result.keywords == []


def test_falls_back_when_structured_output_is_empty():
    llm = _LLMStub(SummaryResult(summary="   ", importance=5, keywords=["x"]), plain="폴백 요약")
    result = _summarize(llm)
    assert result.summary == "폴백 요약"


# --- 토큰 수 ----------------------------------------------------------------

def test_estimate_overestimates_korean():
    # 실측(claude-opus-4-8): 200자 한국어 = 230토큰. 근사치는 그보다 커야 안전하다.
    text = "그는 조용히 고개를 돌렸다. " * 15
    assert estimate_tokens(text[:200]) >= 230


def test_empty_text_is_zero_tokens():
    assert count_tokens("") == 0


def test_count_tokens_falls_back_without_client(monkeypatch):
    monkeypatch.setattr(token_counter, "_get_client", lambda: None)
    assert count_tokens("도서관") == estimate_tokens("도서관")