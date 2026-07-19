import os
from dataclasses import replace

from langchain_openai import ChatOpenAI

from app.llm.profiles import LLMProfile


def build_llm(profile: LLMProfile) -> ChatOpenAI:
    # Vercel AI Gateway(OpenAI 호환) 경유. 키 하나 + base_url 하나로 여러 제공사 모델 호출.
    api_key = os.getenv("AI_GATEWAY_API_KEY")
    if not api_key:
        raise RuntimeError("AI_GATEWAY_API_KEY가 비어있습니다. .env를 확인하세요.")
    base_url = os.getenv("AI_GATEWAY_BASE_URL")
    if not base_url:
        # 없으면 ChatOpenAI가 api.openai.com으로 조용히 붙어 게이트웨이 키로 401이 난다.
        raise RuntimeError("AI_GATEWAY_BASE_URL이 비어있습니다. .env를 확인하세요.")

    optional = {}
    if profile.top_p is not None:
        optional["top_p"] = profile.top_p
    if profile.presence_penalty is not None:
        optional["presence_penalty"] = profile.presence_penalty
    if profile.frequency_penalty is not None:
        optional["frequency_penalty"] = profile.frequency_penalty

    return ChatOpenAI(
        model=profile.model,
        api_key=api_key,
        base_url=base_url,
        temperature=profile.temperature,
        max_tokens=profile.max_tokens,
        timeout=profile.timeout,
        max_retries=profile.max_retries,
        use_responses_api=profile.use_responses_api,
        **optional,
    )


def get_chat_llm(app, model: str | None) -> ChatOpenAI:
    # 채팅방별 모델 선택. slug마다 ChatOpenAI를 한 번만 만들어 캐시(warm httpx 커넥션 풀 재사용).
    # 모델 카탈로그(선택지·기본값·검증)는 자바 백엔드가 소유한다. 여기선 받은 slug를 그대로
    # 실행하고, 잘못된 slug는 게이트웨이 호출에서 시끄럽게 실패하게 둔다(조용한 폴백 금지 —
    # 내부 경계에서 잘못된 값은 배선 버그라서 숨기면 안 됨). model이 None이면 CHAT_MODEL
    # 프로필 기본값(개발/전환기용 폴백). 호출자가 내부 인증을 통과한 백엔드뿐이므로
    # 캐시 크기는 백엔드 카탈로그 크기로 유한하다.
    slug = model or app.state.chat_profile.model
    cache = app.state.chat_llm_cache
    llm = cache.get(slug)
    if llm is None:
        # 기동 시 로드해 둔 CHAT_ 프로필에서 model만 갈아끼운다.
        profile = replace(app.state.chat_profile, model=slug)
        llm = build_llm(profile)
        cache[slug] = llm
    return llm
