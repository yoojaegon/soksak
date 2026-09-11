from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.core.config import setup_logging
from app.llm import build_llm, check_api_keys, load_profile

setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_dotenv()
    # 채팅은 방마다 모델을 고를 수 있어 slug별로 LLM을 캐시한다.
    # CHAT_ 프로필은 기동 시 한 번 로드해 두고 model 필드만 갈아끼워 재사용(get_chat_llm 참고).
    # max_tokens는 길이 조절이 아니라 폭주 방지 상한이다(과금은 실제 생성량 기준).
    # 채팅은 thinking 켜진 모델이 추론에 다 써도 본문이 남게 넉넉히 두고,
    # 길이는 프롬프트로 잡는다. 온도는 캐릭터 연기라 높게.
    # timeout/max_retries는 한 쌍으로 읽을 것 — 곱이 백엔드 read-timeout(60초)을 넘으면
    # 초과분은 아무도 안 듣는 호출이 된다(profiles.load_profile 주석의 재시도 예산 규칙).
    # 기본 모델은 카탈로그 기본값(ModelCatalog.DEFAULT_ID)과 맞춰 둔다. 실제로는 백엔드가
    # 방마다 slug을 실어 보내므로 이 값이 쓰이는 건 slug 없이 들어온 요청뿐이다.
    app.state.chat_profile = load_profile(
        "CHAT_",
        "chat",
        model="google/gemini-3.5-flash-lite",
        temperature=0.8,
        max_tokens=20000,
        timeout=60,
        max_retries=0,
    )
    app.state.chat_llm_cache = {}
    # 요약은 SUMMARY_ 고정 프로필 그대로(모델 선택은 채팅 전용).
    # 요약문은 매 턴 시스템 프롬프트에 재주입되니 상한이 곧 실질 길이 제한이다.
    # 잘리면 다음 턴의 기존 요약으로 들어가 오염이 누적되므로 넉넉히, 단 채팅만큼은 아니게.
    # 사실 기록이라 온도는 낮게.
    # 요약은 비스트리밍(invoke)이라 read 한 번이 생성 전체를 덮어 30초로는 빡빡하다.
    # 재시도가 실제로 도는 건 비스트리밍인 이쪽뿐인데(스트리밍은 헤더가 온 뒤엔 재시도 없음),
    # 60초를 다 쓰고 실패하면 백엔드도 그 시점에 포기하므로 뒤 시도는 전부 헛돈다 → 0.
    # ⚠️ 요약 모델은 thinking이 꺼진 것으로 고를 것. 추론 토큰이 max_tokens에서 나가므로
    # thinking 모델을 물리면 2000을 추론이 다 먹고 요약문이 빈 채로 돌아온다(증상이 안 보인다).
    # Claude는 명시하지 않으면 추론하지 않고, Haiku는 그중 가장 싸다(2026-09-09 실측).
    summary_profile = load_profile(
        "SUMMARY_",
        "summary",
        model="anthropic/claude-haiku-4.5",
        temperature=0.3,
        max_tokens=2000,
        timeout=60,
        max_retries=0,
    )
    # 키는 여기서 확인한다. 안 하면 이 서버가 확실히 쓰는 제공사의 키가 비어 있어도
    # 기동은 깨끗하게 성공하고 첫 메시지에서 500이 난다(factory.check_api_keys 주석).
    check_api_keys(app.state.chat_profile.model, summary_profile.model)
    app.state.summary_llm = build_llm(summary_profile)
    yield


app = FastAPI(lifespan=lifespan, title="Soksak LLM Service")
app.include_router(chat_router)
