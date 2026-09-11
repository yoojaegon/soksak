import logging
import os
from dataclasses import dataclass, replace
from typing import Callable

from langchain_anthropic import ChatAnthropic
from langchain_core.language_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI

from app.llm.profiles import LLMProfile

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class _Provider:
    api_key_env: str
    # slug의 버전 표기를 그대로 둘지. Gemini는 점을 쓰고(gemini-3.5-flash),
    # Anthropic은 하이픈을 쓴다(claude-opus-4-8).
    keeps_dotted_version: bool
    # 프롬프트 캐싱을 '명시적으로' 켜야 하는가. 켜는 방법이 제공사 어휘라서(Anthropic은
    # 블록에 cache_control) 프롬프트를 어떤 모양으로 쌀지가 여기서 갈린다.
    explicit_prompt_cache: bool
    # (프로필, 제공사 모델 ID, API 키) → 채팅 모델. 제공사마다 클래스도 인자 이름도
    # 달라서, 그 차이를 전부 이 함수 안에 가둔다(build_llm에 제공사 분기를 만들지 않는다).
    build: Callable[[LLMProfile, str, str], BaseChatModel]


# 샘플링 파라미터(temperature/top_p)를 받지 않는 모델. 제공사에 따라 증상이 다르다
# (2026-09-10 네이티브 실측):
#   Claude 4.7↑   400 "`temperature` is deprecated for this model." — 요청 자체가 죽는다
#   flash-lite    무시하고 매 요청 UserWarning("uses fixed sampling defaults")만 남는다
# 어느 쪽이든 보낼 이유가 없다. 모델 쪽 제약이라 엔드포인트를 옮겨도 그대로다.
# ⚠️ Claude 쪽 규칙은 "4.7 이상 및 5 계열은 샘플링 미지원"이다 — 카탈로그에 새 모델을
# 올릴 때 이 규칙으로 판단할 것(4.6과 haiku-4.5는 아직 허용).
# Claude의 깊이 조절은 샘플링이 아니라 output_config.effort(ChatAnthropic의
# reasoning_effort)인데, 추론 레벨을 노출하는 건 별도 작업이다.
_NO_SAMPLING = frozenset({
    "anthropic/claude-opus-4.8",
    "anthropic/claude-opus-4.7",
    "google/gemini-3.5-flash-lite",
})

# 추론(thinking)을 꺼야 하는 모델. 지금 하는 건 호환 엔드포인트 시절의 거동을 네이티브에서
# 그대로 재현하는 것뿐이고, 추론 레벨을 방마다 고르게 하는 건 나중 작업이다.
# 2026-09-10 네이티브 실측(같은 프롬프트, 추론 토큰):
#   gemini-3.5-flash       설정 없으면 673 → thinking_budget=0이면 0. 꺼야 한다
#   gemini-3.1-pro-preview 544. 끌 수 없다 — 답변이 max_tokens를 넘어 잘릴 여지가 남는다
#   gemini-3.5-flash-lite  0. 추론 자체가 없다
#   Claude                 0. 명시하지 않으면 추론하지 않는다(opus-4.8도 마찬가지)
# ⚠️ Gemini 3 계열의 공식 노브는 thinking_budget이 아니라 thinking_level
# (minimal/low/medium/high)인데 거기엔 "끔"이 없다. budget=0이 아직 먹으므로 이 값을 쓴다.
_THINKING_OFF = frozenset({"google/gemini-3.5-flash"})


def _common_kwargs(profile: LLMProfile, model: str, api_key: str) -> dict:
    """두 제공사 클래스가 같은 이름으로 받아주는 인자들.

    이름이 겹치는 건 우연이 아니라 별칭이다(ChatAnthropic.max_tokens ← max_tokens_to_sample,
    ChatGoogleGenerativeAI.max_output_tokens ← max_tokens 등, 둘 다 populate_by_name).
    두 클래스 모두 모르는 인자를 조용히 버리므로(extra="ignore") 오타는 예외가 아니라
    무언의 미전송으로 나타난다 — 여기 있는 이름은 전부 실제 필드로 확인한 것이다.
    """
    kwargs = {
        "model": model,
        "api_key": api_key,
        "max_tokens": profile.max_tokens,
        "timeout": profile.timeout,
        "max_retries": profile.max_retries,
    }
    if profile.model not in _NO_SAMPLING:
        kwargs["temperature"] = profile.temperature
        if profile.top_p is not None:
            kwargs["top_p"] = profile.top_p
    return kwargs


def _build_anthropic(profile: LLMProfile, model: str, api_key: str) -> BaseChatModel:
    # presence_penalty/frequency_penalty는 Anthropic에 없는 노브다. 호환 레이어에선 실려도
    # 무시되는(Ignored) 무효 필드였고 네이티브에선 인자 자체가 없으므로 싣지 않는다.
    return ChatAnthropic(**_common_kwargs(profile, model, api_key))


def _build_google(profile: LLMProfile, model: str, api_key: str) -> BaseChatModel:
    kwargs = _common_kwargs(profile, model, api_key)
    if profile.presence_penalty is not None:
        kwargs["presence_penalty"] = profile.presence_penalty
    if profile.frequency_penalty is not None:
        kwargs["frequency_penalty"] = profile.frequency_penalty
    if profile.model in _THINKING_OFF:
        kwargs["thinking_budget"] = 0
    return ChatGoogleGenerativeAI(**kwargs)


# 제공사 네이티브 SDK 직결. 예전엔 Vercel AI Gateway 하나를 거쳤고, 게이트웨이 크레딧이
# 끊긴 뒤엔 제공사별 OpenAI 호환 엔드포인트를 썼다. 호환 레이어는 response_format(구조화
# 출력)과 프롬프트 캐싱을 지원하지 않아 네이티브로 옮겼다.
_PROVIDERS: dict[str, _Provider] = {
    "anthropic/": _Provider(
        api_key_env="ANTHROPIC_API_KEY",
        keeps_dotted_version=False,
        explicit_prompt_cache=True,
        build=_build_anthropic,
    ),
    "google/": _Provider(
        api_key_env="GOOGLE_API_KEY",
        keeps_dotted_version=True,
        # Gemini는 암시적 캐싱이 기본 on이라 켤 게 없다(프리픽스가 같으면 알아서 깎인다).
        # 명시적 캐싱(CachedContent)은 저장 시간당 요금이 붙어 방 하나당 프롬프트 하나인
        # 이 규모엔 손해다. 그래서 False는 "못 한다"가 아니라 "할 필요가 없다"는 뜻이다.
        explicit_prompt_cache=False,
        build=_build_google,
    ),
}


def _resolve(slug: str) -> tuple[_Provider, str]:
    """soksak slug을 (제공사, 제공사 모델 ID)로 푼다.

    slug은 게이트웨이 시절 표기를 그대로 물려받은 소삭 내부 식별자다. DB(chat_room.model)와
    백엔드 카탈로그가 이 표기를 쓰고 있어 유지하는 편이 싸고, 제공사 ID로의 변환은
    여기서만 안다.
    """
    for prefix, provider in _PROVIDERS.items():
        if slug.startswith(prefix):
            model = slug[len(prefix):]
            return provider, model if provider.keeps_dotted_version else model.replace(".", "-")
    # 카탈로그(백엔드 소유)에 없는 값이 여기까지 온 건 배선 버그다. 조용히 폴백하지 않는다.
    raise RuntimeError(f"제공사를 알 수 없는 모델 slug입니다: {slug}")


def _require_api_key(provider: _Provider) -> str:
    api_key = os.getenv(provider.api_key_env)
    if not api_key:
        raise RuntimeError(f"{provider.api_key_env}가 비어있습니다. .env를 확인하세요.")
    return api_key


def check_api_keys(*required_slugs: str) -> None:
    """기동 시 키를 확인한다.

    게이트웨이 시절엔 키가 하나라 없으면 기동이 시끄럽게 죽었는데, 제공사가 둘로 늘면서
    "기동은 성공하고 첫 메시지에서 500"이 될 수 있게 됐다. 모델 카탈로그는 백엔드가
    소유하므로 여기서 어느 제공사가 실제로 쓰일지는 알 수 없다 → 이 서버가 확실히 쓰는
    slug(채팅 폴백·요약 모델)의 키는 없으면 죽이고, 나머지 제공사는 경고만 남긴다.
    """
    required_envs = set()
    for slug in required_slugs:
        provider, _ = _resolve(slug)
        _require_api_key(provider)
        required_envs.add(provider.api_key_env)

    for provider in _PROVIDERS.values():
        if provider.api_key_env in required_envs:
            continue
        if not os.getenv(provider.api_key_env):
            logger.warning(
                "%s가 비어있다. 카탈로그에 이 제공사 모델이 있으면 그 방의 첫 메시지에서 실패한다.",
                provider.api_key_env,
            )


def build_llm(profile: LLMProfile) -> BaseChatModel:
    provider, model = _resolve(profile.model)
    return provider.build(profile, model, _require_api_key(provider))


def resolve_slug(app, model: str | None) -> str:
    """요청에 실려 온 모델 slug. 없으면 CHAT_ 프로필 기본값(개발/전환기용 폴백).

    한 요청에서 slug을 두 번 이상 봐야 해서(모델 객체를 얻을 때, 캐싱 지원 여부를 물을 때)
    폴백 규칙을 여기 한 곳에만 둔다.
    """
    return model or app.state.chat_profile.model


def supports_prompt_cache(slug: str) -> bool:
    """이 모델에 프롬프트 캐싱을 '명시적으로' 켜야 하는가(_Provider.explicit_prompt_cache).

    호출자는 제공사를 몰라도 되고, 알 필요도 없다 — 아는 자리는 여기 하나다.
    """
    return _resolve(slug)[0].explicit_prompt_cache


def get_chat_llm(app, model: str | None) -> BaseChatModel:
    # 채팅방별 모델 선택. slug마다 모델 객체를 한 번만 만들어 캐시(warm 커넥션 재사용).
    # 모델 카탈로그(선택지·기본값·검증)는 자바 백엔드가 소유한다. 여기선 받은 slug를 그대로
    # 실행하고, 잘못된 slug는 시끄럽게 실패하게 둔다(조용한 폴백 금지 — 내부 경계에서
    # 잘못된 값은 배선 버그라서 숨기면 안 됨). 호출자가 내부 인증을 통과한 백엔드뿐이므로
    # 캐시 크기는 백엔드 카탈로그 크기로 유한하다.
    slug = resolve_slug(app, model)
    cache = app.state.chat_llm_cache
    llm = cache.get(slug)
    if llm is None:
        # 기동 시 로드해 둔 CHAT_ 프로필에서 model만 갈아끼운다. 나머지(추론·샘플링)는
        # slug에서 유도되므로 slug 하나로 캐시 키가 유지된다.
        llm = build_llm(replace(app.state.chat_profile, model=slug))
        cache[slug] = llm
    return llm
