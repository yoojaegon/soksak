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

    top_p: Optional[float] = None
    presence_penalty: Optional[float] = None
    frequency_penalty: Optional[float] = None
    # 추론(thinking) 설정은 프로필에 없다 — 제공사마다 인자도 값의 의미도 달라서
    # factory가 slug를 보고 정한다. 방마다 추론 레벨을 고르게 하는 건 별도 작업.


# 빈 문자열은 "설정 안 함"으로 본다. compose가 미설정 변수를 넘기면 값이 ""로 들어오는데
# (docker-compose.yml의 SUMMARY_MODEL 등), os.getenv의 기본값 인자는 그걸 값으로 받아들여
# model=""로 기동하거나 int("")로 터진다.
def _env(key: str) -> Optional[str]:
    value = os.getenv(key)
    return value if value else None


# 기본값은 프로필별로 호출부(main.py)가 정한다. 환경변수가 있으면 그게 이기므로
# 값을 바꿔 실험할 때는 .env에 한 줄 넣으면 되고, 평상시엔 코드가 단일 출처다.
# model/temperature/max_tokens는 채팅(길고 창의적)과 요약(짧고 사실적)의 정답이 달라
# 기본값을 공유하면 한쪽이 반드시 틀린다 → 필수 인자로 둬서 새 프로필이 조용히
# 엉뚱한 값을 물려받는 걸 막는다. model은 특히 그렇다 — 요약은 thinking이 켜진 모델을
# 물려받으면 추론이 max_tokens를 먹어 요약문이 빈 채로 돌아온다.
#
# 재시도 예산 규칙: timeout × (max_retries+1) ≤ 호출자의 인내심(백엔드 application.yml의
# ai-server.read-timeout). 초과분은 백엔드가 이미 포기한 뒤에 나가는, 아무도 안 듣는
# 유료 호출이다. 지금은 timeout=60 == read-timeout=60이라 여유가 0 → max_retries=0.
# 재시도를 되살리고 싶으면 값만 올리지 말고 per-attempt timeout부터 내려야 한다(예: 28×2=56).
def load_profile(
    prefix: str,
    name: str,
    *,
    model: str,
    temperature: float,
    max_tokens: int,
    timeout: int = 30,
    max_retries: int = 0,
) -> LLMProfile:
    return LLMProfile(
        name=name,
        model=_env(f"{prefix}MODEL") or model,
        temperature=float(_env(f"{prefix}TEMPERATURE") or temperature),
        max_tokens=int(_env(f"{prefix}MAX_TOKENS") or max_tokens),
        timeout=int(_env(f"{prefix}TIMEOUT") or timeout),
        max_retries=int(_env(f"{prefix}MAX_RETRIES") or max_retries),
        top_p=float(_env(f"{prefix}TOP_P")) if _env(f"{prefix}TOP_P") else None,
        presence_penalty=float(_env(f"{prefix}PRESENCE_PENALTY")) if _env(f"{prefix}PRESENCE_PENALTY") else None,
        frequency_penalty=float(_env(f"{prefix}FREQUENCY_PENALTY")) if _env(f"{prefix}FREQUENCY_PENALTY") else None,
    )
