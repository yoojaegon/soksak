from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.core.config import setup_logging
from app.llm import build_llm, load_profile

setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_dotenv()
    # 채팅은 방마다 모델을 고를 수 있어 slug별로 LLM을 캐시한다.
    # CHAT_ 프로필은 기동 시 한 번 로드해 두고 model 필드만 갈아끼워 재사용(get_chat_llm 참고).
    app.state.chat_profile = load_profile("CHAT_", "chat")
    app.state.chat_llm_cache = {}
    # 요약은 SUMMARY_ 고정 프로필 그대로(모델 선택은 채팅 전용).
    app.state.summary_llm = build_llm(load_profile("SUMMARY_", "summary"))
    yield


app = FastAPI(lifespan=lifespan, title="Soksak LLM Service")
app.include_router(chat_router)
