import os
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class LLMProfile:
    name: str
    model: str
    temperature: float
    max_tokens: int
    timeout: int
    max_retries: int
    # 게이트웨이는 chat completions만 지원 — Responses API는 명시적으로 켤 때만 쓴다.
    use_responses_api: bool = False

    top_p: Optional[float] = None
    presence_penalty: Optional[float] = None
    frequency_penalty: Optional[float] = None


def _bool_env(key: str, default: bool) -> bool:
    return os.getenv(key, str(default)).lower() in {"1", "true", "yes"}


# 기본값은 프로필별로 호출부(main.py)가 정한다. 환경변수가 있으면 그게 이기므로
# 값을 바꿔 실험할 때는 .env에 한 줄 넣으면 되고, 평상시엔 코드가 단일 출처다.
# temperature/max_tokens는 채팅(길고 창의적)과 요약(짧고 사실적)의 정답이 달라
# 기본값을 공유하면 한쪽이 반드시 틀린다 → 필수 인자로 둬서 새 프로필이 조용히
# 엉뚱한 값을 물려받는 걸 막는다.
def load_profile(
    prefix: str,
    name: str,
    *,
    temperature: float,
    max_tokens: int,
    model: str = "openai/gpt-4o-mini",
    timeout: int = 30,
    max_retries: int = 2,
) -> LLMProfile:
    return LLMProfile(
        name=name,
        model=os.getenv(f"{prefix}MODEL", model),
        temperature=float(os.getenv(f"{prefix}TEMPERATURE", str(temperature))),
        max_tokens=int(os.getenv(f"{prefix}MAX_TOKENS", str(max_tokens))),
        timeout=int(os.getenv(f"{prefix}TIMEOUT", str(timeout))),
        max_retries=int(os.getenv(f"{prefix}MAX_RETRIES", str(max_retries))),
        use_responses_api=_bool_env(f"{prefix}USE_RESPONSES_API", False),
        top_p=float(os.getenv(f"{prefix}TOP_P")) if os.getenv(f"{prefix}TOP_P") else None,
        presence_penalty=float(os.getenv(f"{prefix}PRESENCE_PENALTY")) if os.getenv(f"{prefix}PRESENCE_PENALTY") else None,
        frequency_penalty=float(os.getenv(f"{prefix}FREQUENCY_PENALTY")) if os.getenv(f"{prefix}FREQUENCY_PENALTY") else None,
    )
