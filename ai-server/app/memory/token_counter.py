"""요약문의 토큰 수 측정.

용도는 하나다 — 백엔드가 "어느 요약까지 프롬프트에 실을지" 예산으로 판단할 수 있게,
요약을 만든 자리에서 그 길이를 재서 함께 넘긴다. 요약은 한 번 만들면 내용이 안 바뀌므로
생성 시점에 한 번만 재두면 이후엔 정수 덧셈만 하면 된다.

채팅은 게이트웨이를 거치지만 count_tokens는 Anthropic 직결 엔드포인트라 전용 키를 쓴다
(무료, 채팅 호출과 레이트 리밋도 별개).

기준 모델을 하나로 고정하는 이유: 방마다 모델이 다른데 모델별로 재두면 카탈로그가 바뀔
때마다 과거 요약을 전부 다시 세야 한다. 과소추정만 사고(컨텍스트 초과)를 내고 과대추정은
컨텍스트를 조금 남길 뿐이라, 토큰을 가장 많이 먹는 모델 하나로 재서 나머지 모델에선
넉넉하게 잡히도록 둔다.
"""

from __future__ import annotations

import logging
import math
import os
import threading

logger = logging.getLogger(__name__)

# 기준 토크나이저. 게이트웨이 slug(anthropic/claude-opus-4.8)가 아니라 Anthropic 직접 ID다.
_DEFAULT_MODEL = "claude-opus-4-8"

# 세는 데 오래 매달릴 이유가 없다 — 실패하면 근사치로 넘어가면 그만이다.
_TIMEOUT_SECONDS = 10.0
_MAX_RETRIES = 1

# 근사식 계수. 2026-09-01 실측(claude-opus-4-8, 한국어): 본문이 글자당 1.12토큰,
# 메시지 한 건당 고정 오버헤드 7토큰. 둘 다 올려잡아 과대추정 쪽으로 기울였다.
# 영어는 글자당 0.35 수준이라 이 식이 크게 과대추정하는데, 그 방향은 안전해서 둔다.
_TOKENS_PER_CHAR = 1.15
_MESSAGE_OVERHEAD = 10

_client = None
_client_lock = threading.Lock()
_client_failed = False


def estimate_tokens(text: str) -> int:
    """API 없이 계산하는 근사치. 항상 실제보다 넉넉하게 나온다."""
    return math.ceil(len(text) * _TOKENS_PER_CHAR) + _MESSAGE_OVERHEAD


def _get_client():
    """Anthropic 클라이언트를 처음 쓸 때 한 번만 만든다.

    키가 없거나 SDK 초기화가 실패하면 None을 돌려주고 다시 시도하지 않는다. 매 요약마다
    같은 실패를 반복해 로그를 채우는 것보다 조용히 근사치로 도는 편이 낫다.
    """
    global _client, _client_failed

    if _client is not None or _client_failed:
        return _client

    with _client_lock:
        if _client is not None or _client_failed:
            return _client

        if not os.getenv("ANTHROPIC_API_KEY"):
            logger.info("ANTHROPIC_API_KEY가 없어 토큰 수를 근사치로 계산한다.")
            _client_failed = True
            return None

        try:
            from anthropic import Anthropic

            _client = Anthropic(timeout=_TIMEOUT_SECONDS, max_retries=_MAX_RETRIES)
        except Exception:
            logger.exception("Anthropic 클라이언트 생성 실패 — 토큰 수는 근사치로 계산한다.")
            _client_failed = True

        return _client


def count_tokens(text: str) -> int:
    """요약문의 토큰 수. 측정에 실패하면 근사치로 대체한다.

    토큰 수는 예산 계산용 보조값이지 요약의 일부가 아니다. 여기서 예외를 올려보내면
    측정 실패가 요약 실패가 되어버리므로, 실패는 전부 삼키고 근사치를 돌려준다.
    """
    if not text:
        return 0

    client = _get_client()
    if client is None:
        return estimate_tokens(text)

    model = os.getenv("TOKEN_COUNT_MODEL") or _DEFAULT_MODEL
    try:
        response = client.messages.count_tokens(
            model=model,
            messages=[{"role": "user", "content": text}],
        )
        return response.input_tokens
    except Exception:
        logger.warning("count_tokens 호출 실패 (model=%s) — 근사치로 대체", model, exc_info=True)
        return estimate_tokens(text)