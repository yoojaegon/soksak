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
    use_responses_api: bool = True

    top_p: Optional[float] = None
    presence_penalty: Optional[float] = None
    frequency_penalty: Optional[float] = None


def _bool_env(key: str, default: bool) -> bool:
    return os.getenv(key, str(default)).lower() in {"1", "true", "yes"}


def load_profile(prefix: str, name: str) -> LLMProfile:
    return LLMProfile(
        name=name,
        model=os.getenv(f"{prefix}MODEL", "gpt-4o-mini"),
        temperature=float(os.getenv(f"{prefix}TEMPERATURE", "0.3")),
        max_tokens=int(os.getenv(f"{prefix}MAX_TOKENS", "512")),
        timeout=int(os.getenv(f"{prefix}TIMEOUT", "30")),
        max_retries=int(os.getenv(f"{prefix}MAX_RETRIES", "2")),
        use_responses_api=_bool_env(f"{prefix}USE_RESPONSES_API", True),
        top_p=float(os.getenv(f"{prefix}TOP_P")) if os.getenv(f"{prefix}TOP_P") else None,
        presence_penalty=float(os.getenv(f"{prefix}PRESENCE_PENALTY")) if os.getenv(f"{prefix}PRESENCE_PENALTY") else None,
        frequency_penalty=float(os.getenv(f"{prefix}FREQUENCY_PENALTY")) if os.getenv(f"{prefix}FREQUENCY_PENALTY") else None,
    )
