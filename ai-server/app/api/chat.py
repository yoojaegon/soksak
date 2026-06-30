import hmac
import os

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import StreamingResponse
from langchain.messages import AIMessage, HumanMessage
from pydantic import BaseModel

from app.chains.chat import chat, chat_stream
from app.memory.summarizer import ConversationSummarizer
from app.prompts.config import PromptConfig


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


class SummarizeRequest(BaseModel):
    existing_summary: str | None = None
    new_messages: list[Message]


@router.post("/chat")
def chat_endpoint(request: ChatRequest, http_request: Request):
    try:
        reply = chat(
            llm=http_request.app.state.chat_llm,
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
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    return {"answer": reply}


@router.post("/chat/stream")
def chat_stream_endpoint(request: ChatRequest, http_request: Request):
    def event_source():
        try:
            for token in chat_stream(
                llm=http_request.app.state.chat_llm,
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
        except Exception as exc:
            yield f"event: error\ndata: {exc}\n\n"

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
        summary = summarizer.update(
            existing_summary=request.existing_summary,
            new_turns=turns,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    return {"summary": summary}
