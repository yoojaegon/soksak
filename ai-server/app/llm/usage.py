"""턴당 토큰 사용량 로깅.

프롬프트 캐싱은 **실패해도 아무도 안 알려준다** — 요청은 계속 성공하고 요금만 오른다.
전형적인 사고는 "처음엔 됐는데 나중에 프롬프트 조립을 건드리면서 깨진 걸 몇 달 몰랐다"
쪽이라, 캐싱을 켜기 전에 눈부터 만들어 둔다. 이 로그가 캐싱이 도는지 아는 유일한 근거다.

⚠️ LangChain의 usage_metadata는 벤더 원본과 의미가 다르다. Anthropic 원본의 input_tokens는
'캐시를 뺀 나머지'지만, LangChain은 거기에 cache_read/cache_creation을 도로 더해 **전체 입력**을
넣는다(langchain_anthropic._create_usage_metadata). Gemini도 prompt_token_count가 캐시분을
포함하므로 같은 규약이다. 그래서 미캐시분은 빼서 구한다.

제공사 둘 다 input_token_details.cache_read 로 정규화돼 오기 때문에 이 한 함수가
Claude의 명시적 캐싱과 Gemini의 암시적 캐싱을 같이 본다. cache_creation은 Anthropic에만 있다
(암시적 캐싱은 쓰기 개념이 없다).
"""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


def _model_label(llm: Any) -> str:
    # 소삭 slug(anthropic/claude-opus-4.8)가 아니라 제공사 모델 ID다 — llm 객체가 아는 건
    # 변환된 쪽뿐이다. 변환 규칙은 factory._resolve 하나뿐이라 역추적은 어렵지 않다.
    model = getattr(llm, "model", None) or "unknown"
    return str(model).removeprefix("models/")  # Gemini는 "models/" 접두사를 붙여 둔다


def log_usage(llm: Any, usage: dict | None) -> None:
    """한 턴의 토큰 사용량을 한 줄로 남긴다. 실패해도 대화를 깨뜨리지 않는다."""
    if not usage:
        return
    try:
        details = usage.get("input_token_details") or {}
        cache_read = details.get("cache_read") or 0
        # Anthropic이 TTL별 내역(5m/1h)을 주면 LangChain이 cache_creation을 0으로 비우고
        # 그쪽에만 넣는다 — 둘 중 값이 있는 쪽을 쓴다.
        cache_write = (details.get("cache_creation") or 0) or (
            (details.get("ephemeral_5m_input_tokens") or 0)
            + (details.get("ephemeral_1h_input_tokens") or 0)
        )
        total_in = usage.get("input_tokens") or 0
        uncached = max(total_in - cache_read - cache_write, 0)
        reasoning = (usage.get("output_token_details") or {}).get("reasoning") or 0

        logger.info(
            "토큰 model=%s 입력=%d(캐시읽기=%d 캐시쓰기=%d 미캐시=%d) 출력=%d(추론=%d)",
            _model_label(llm),
            total_in,
            cache_read,
            cache_write,
            uncached,
            usage.get("output_tokens") or 0,
            reasoning,
        )
    except Exception:
        # 계측이 대화를 깨는 건 본말전도다.
        logger.warning("토큰 사용량 로깅 실패", exc_info=True)
