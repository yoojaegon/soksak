"""토큰 사용량 로깅 — 제공사별 usage_metadata 모양에서 값을 제대로 뽑는지.

실제 캐시 적중은 API를 불러야 알 수 있어 여기선 못 본다. 여기서 지키는 건 그 앞 단계다:
제공사가 주는 모양이 바뀌거나 키를 잘못 읽으면 **로그가 조용히 0을 찍는다**. 캐싱이 깨진
것과 계측이 깨진 것이 똑같이 보이면 계측이 아니다.
"""

import logging

from langchain.messages import AIMessageChunk

from app.chains.chat import chat_stream
from app.llm.usage import log_usage


class _FakeLLM:
    def __init__(self, model: str) -> None:
        self.model = model


class _FakeStreamLLM(_FakeLLM):
    """청크 목록을 그대로 흘려보내는 LLM."""

    def __init__(self, model: str, chunks: list[AIMessageChunk]) -> None:
        super().__init__(model)
        self._chunks = chunks

    def stream(self, messages):
        yield from self._chunks


def _line(caplog) -> str:
    return caplog.records[0].getMessage() if caplog.records else ""


def test_anthropic_shape(caplog):
    # Anthropic: input_tokens는 LangChain이 캐시분을 도로 더한 '전체 입력'이다.
    usage = {
        "input_tokens": 5000,
        "output_tokens": 300,
        "input_token_details": {"cache_read": 4000, "cache_creation": 500},
    }
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        log_usage(_FakeLLM("claude-opus-4-8"), usage)
    line = _line(caplog)
    assert "model=claude-opus-4-8" in line
    assert "입력=5000" in line
    assert "캐시읽기=4000" in line and "캐시쓰기=500" in line
    assert "미캐시=500" in line  # 5000 - 4000 - 500


def test_anthropic_ttl_breakdown(caplog):
    # TTL별 내역이 오면 LangChain이 cache_creation을 0으로 비우고 그쪽에만 넣는다.
    usage = {
        "input_tokens": 5000,
        "output_tokens": 10,
        "input_token_details": {
            "cache_read": 0,
            "cache_creation": 0,
            "ephemeral_5m_input_tokens": 4500,
            "ephemeral_1h_input_tokens": 0,
        },
    }
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        log_usage(_FakeLLM("claude-opus-4-8"), usage)
    line = _line(caplog)
    assert "캐시쓰기=4500" in line
    assert "미캐시=500" in line


def test_google_shape_with_reasoning(caplog):
    # Gemini는 암시적 캐싱이라 cache_read만 있고 쓰기 개념이 없다. 추론 토큰은 출력의 부분집합.
    usage = {
        "input_tokens": 3000,
        "output_tokens": 700,
        "input_token_details": {"cache_read": 2000},
        "output_token_details": {"reasoning": 544},
    }
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        log_usage(_FakeLLM("models/gemini-3.1-pro-preview"), usage)
    line = _line(caplog)
    assert "model=gemini-3.1-pro-preview" in line  # "models/" 접두사는 벗긴다
    assert "캐시읽기=2000" in line and "캐시쓰기=0" in line
    assert "미캐시=1000" in line
    assert "추론=544" in line


def test_no_usage_logs_nothing(caplog):
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        log_usage(_FakeLLM("claude-opus-4-8"), None)
        log_usage(_FakeLLM("claude-opus-4-8"), {})
    assert not caplog.records


def _anthropic_chunks() -> list[AIMessageChunk]:
    # Anthropic은 스트리밍 중간 청크에 usage_metadata를 싣지 않는다 — 전부 마지막에 온다.
    return [
        AIMessageChunk(content="안"),
        AIMessageChunk(content="녕"),
        AIMessageChunk(
            content="",
            usage_metadata={
                "input_tokens": 5000,
                "output_tokens": 300,
                "total_tokens": 5300,
                "input_token_details": {"cache_read": 4000, "cache_creation": 0},
            },
        ),
    ]


def test_stream_logs_usage_from_final_chunk(caplog):
    llm = _FakeStreamLLM("claude-opus-4-8", _anthropic_chunks())
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        assert "".join(chat_stream(llm, "페르소나", "안녕")) == "안녕"
    line = _line(caplog)
    assert "입력=5000" in line and "출력=300" in line
    assert "캐시읽기=4000" in line


def test_stream_aborted_before_usage_logs_nothing(caplog):
    # 클라이언트가 중간에 끊으면 Anthropic의 사용량 청크는 영영 안 온다. 그때 0을 찍으면
    # 캐시읽기=0이 '캐싱 고장'과 구분되지 않는다 — 모르는 건 모른다고 두고 침묵한다.
    llm = _FakeStreamLLM("claude-opus-4-8", _anthropic_chunks())
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        stream = chat_stream(llm, "페르소나", "안녕")
        assert next(stream) == "안"
        stream.close()
    assert not caplog.records


def test_stream_accumulates_per_chunk_deltas(caplog):
    # Gemini는 청크마다 델타로 준다 — 마지막 것만 잡으면 입력 토큰을 통째로 놓친다.
    llm = _FakeStreamLLM(
        "models/gemini-3.1-pro-preview",
        [
            AIMessageChunk(
                content="안",
                usage_metadata={
                    "input_tokens": 3000,
                    "output_tokens": 1,
                    "total_tokens": 3001,
                    "input_token_details": {"cache_read": 2000},
                },
            ),
            AIMessageChunk(
                content="녕",
                usage_metadata={
                    "input_tokens": 0,
                    "output_tokens": 699,
                    "total_tokens": 699,
                },
            ),
        ],
    )
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        assert "".join(chat_stream(llm, "페르소나", "안녕")) == "안녕"
    line = _line(caplog)
    assert "입력=3000" in line and "출력=700" in line
    assert "캐시읽기=2000" in line


def test_broken_shape_does_not_raise(caplog):
    # 계측이 대화를 깨선 안 된다.
    with caplog.at_level(logging.INFO, logger="app.llm.usage"):
        log_usage(_FakeLLM("claude-opus-4-8"), {"input_tokens": "몇개더라"})
    assert caplog.records  # 경고는 남되 예외는 안 난다
