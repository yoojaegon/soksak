from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.chat import router as chat_router
from app.core.config import setup_logging
from app.llm import build_llm, load_profile

setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_dotenv()
    app.state.chat_llm = build_llm(load_profile("CHAT_", "chat"))
    app.state.summary_llm = build_llm(load_profile("SUMMARY_", "summary"))
    yield


app = FastAPI(lifespan=lifespan, title="Soksak LLM Service")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(chat_router)
