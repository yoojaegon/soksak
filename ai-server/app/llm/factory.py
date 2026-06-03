import os

from langchain_openai import ChatOpenAI

from app.llm.profiles import LLMProfile


def build_llm(profile: LLMProfile) -> ChatOpenAI:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY가 비어있습니다. .env를 확인하세요.")

    return ChatOpenAI(
        model=profile.model,
        temperature=profile.temperature,
        max_tokens=profile.max_tokens,
        timeout=profile.timeout,
        max_retries=profile.max_retries,
        use_responses_api=profile.use_responses_api,
    )
