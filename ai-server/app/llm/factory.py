import os

from langchain_openai import ChatOpenAI

from app.llm.profiles import LLMProfile


def build_llm(profile: LLMProfile) -> ChatOpenAI:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY가 비어있습니다. .env를 확인하세요.")

    optional = {}
    if profile.top_p is not None:
        optional["top_p"] = profile.top_p
    if profile.presence_penalty is not None:
        optional["presence_penalty"] = profile.presence_penalty
    if profile.frequency_penalty is not None:
        optional["frequency_penalty"] = profile.frequency_penalty

    return ChatOpenAI(
        model=profile.model,
        temperature=profile.temperature,
        max_tokens=profile.max_tokens,
        timeout=profile.timeout,
        max_retries=profile.max_retries,
        use_responses_api=profile.use_responses_api,
        **optional,
    )
