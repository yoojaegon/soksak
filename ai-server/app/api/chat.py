import hmac
import logging
import os

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import StreamingResponse
from langchain.messages import AIMessage, HumanMessage
from pydantic import BaseModel

from app.chains.chat import chat, chat_stream
from app.llm import get_chat_llm
from app.memory.summarizer import ConversationSummarizer
from app.memory.token_counter import count_tokens
from app.prompts.config import PromptConfig

logger = logging.getLogger(__name__)


def verify_internal_secret(x_internal_auth: str = Header(default="")) -> None:
    # 백엔드와 공유하는 정적 시크릿. 양쪽 .env의 INTERNAL_AUTH_SECRET이 일치해야 통과.
    secret = os.environ.get("INTERNAL_AUTH_SECRET", "")
    if not secret or not hmac.compare_digest(x_internal_auth, secret):
        raise HTTPException(status_code=401, detail="unauthorized")


router = APIRouter(dependencies=[Depends(verify_internal_secret)])


class Message(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    persona: str
    user_message: str
    recent_messages: list[Message] = []
    lore_entries: list[str] = []
    summary: str | None = None
    config: PromptConfig = PromptConfig()
    char_name: str | None = None
    user_name: str | None = None
    user_persona: str | None = None
    # 백엔드가 카탈로그에서 검증해 확정한 모델 slug. 카탈로그(선택지·기본값)의 단일 출처는
    # 자바 백엔드다. None이면 CHAT_MODEL 프로필 기본값(개발/전환기용 폴백, get_chat_llm 참고).
    model: str | None = None


class SummarizeRequest(BaseModel):
    # 맥락 파악용으로만 쓰이는 직전 요약 1~2건. 이걸 갱신하는 게 아니라 이번 구간을
    # 따로 요약하고, 누적은 백엔드가 요약을 여러 건 쌓아 두는 방식으로 처리한다.
    previous_summaries: list[str] = []
    new_messages: list[Message]
    # 구버전 백엔드 호환. 갱신형 시절의 단일 요약 필드로, 오면 앞선 기록 한 건으로 취급한다.
    existing_summary: str | None = None

    def context_summaries(self) -> list[str]:
        if self.previous_summaries:
            return self.previous_summaries
        return [self.existing_summary] if self.existing_summary else []


@router.post("/chat")
def chat_endpoint(request: ChatRequest, http_request: Request):
    try:
        reply = chat(
            llm=get_chat_llm(http_request.app, request.model),
            persona=request.persona,
            user_text=request.user_message,
            recent_messages=[m.model_dump() for m in request.recent_messages],
            lore_entries=request.lore_entries,
            summary=request.summary,
            config=request.config,
            user_name=request.user_name,
            user_persona=request.user_persona,
            char_name=request.char_name,
        )
    except Exception:
        logger.exception("chat 처리 실패 (model=%s)", request.model)
        raise HTTPException(status_code=500, detail="internal error")
    return {"answer": reply}


@router.post("/chat/stream")
def chat_stream_endpoint(request: ChatRequest, http_request: Request):
    def event_source():
        try:
            for token in chat_stream(
                llm=get_chat_llm(http_request.app, request.model),
                persona=request.persona,
                user_text=request.user_message,
                recent_messages=[m.model_dump() for m in request.recent_messages],
                lore_entries=request.lore_entries,
                summary=request.summary,
                config=request.config,
                user_name=request.user_name,
                user_persona=request.user_persona,
                char_name=request.char_name,
            ):
                # 토큰에 개행이 있으면 줄마다 data: 를 붙여야 SSE 프레이밍이 안 깨진다.
                for line in token.split("\n"):
                    yield f"data: {line}\n"
                yield "\n"
            yield "data: [DONE]\n\n"
        except Exception:
            logger.exception("chat stream 처리 실패 (model=%s)", request.model)
            yield "event: error\ndata: internal error\n\n"

    return StreamingResponse(event_source(), media_type="text/event-stream")


@router.post("/summarize")
def summarize_endpoint(request: SummarizeRequest, http_request: Request):
    try:
        turns = []
        for m in request.new_messages:
            if m.role == "user":
                turns.append(HumanMessage(content=m.content))
            elif m.role == "assistant":
                turns.append(AIMessage(content=m.content))

        summarizer = ConversationSummarizer(http_request.app.state.summary_llm)
        result = summarizer.summarize(
            previous_summaries=request.context_summaries(),
            new_turns=turns,
        )
    except Exception:
        logger.exception("summarize 처리 실패")
        raise HTTPException(status_code=500, detail="internal error")

    # 토큰 수는 백엔드가 "어느 요약까지 프롬프트에 실을지" 정할 때 쓰는 보조값이다.
    # 측정이 실패해도 근사치가 돌아오므로 여기서 요약이 깨지는 일은 없다.
    return {
        "summary": result.summary,
        "importance": result.importance,
        "keywords": result.keywords,
        "token_count": count_tokens(result.summary),
    }
