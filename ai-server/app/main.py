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
    # max_tokens는 길이 조절이 아니라 폭주 방지 상한이다(과금은 실제 생성량 기준).
    # 채팅은 thinking 켜진 모델이 추론에 다 써도 본문이 남게 넉넉히 두고,
    # 길이는 프롬프트로 잡는다. 온도는 캐릭터 연기라 높게.
    app.state.chat_profile = load_profile(
        "CHAT_", "chat", temperature=0.8, max_tokens=20000, timeout=60
    )
    app.state.chat_llm_cache = {}
    # 요약은 SUMMARY_ 고정 프로필 그대로(모델 선택은 채팅 전용).
    # 요약문은 매 턴 시스템 프롬프트에 재주입되니 상한이 곧 실질 길이 제한이다.
    # 잘리면 다음 턴의 기존 요약으로 들어가 오염이 누적되므로 넉넉히, 단 채팅만큼은 아니게.
    # 사실 기록이라 온도는 낮게.
    # 요약은 비스트리밍(invoke)이라 read 한 번이 생성 전체를 덮어 30초로는 빡빡하다.
    app.state.summary_llm = build_llm(
        load_profile("SUMMARY_", "summary", temperature=0.3, max_tokens=2000, timeout=60)
    )
    yield


app = FastAPI(lifespan=lifespan, title="Soksak LLM Service")
app.include_router(chat_router)
