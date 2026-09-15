"""slug에서 유도되는 모델 설정 — 추론과 샘플링.

추론 레벨은 요청에서 오고(없으면 slug별 기본값), 샘플링 여부는 slug가 정한다. 여기서 막는 건 하나:
**두 제공사 클래스 모두 모르는 인자를 조용히 버린다(extra="ignore")** — 인자 이름이
틀리면 예외가 아니라 "설정이 그냥 안 실린 채 성공"으로 나타나서 눈으로는 안 보인다.
그래서 값을 넣었다는 사실이 아니라 **만들어진 객체의 필드**를 확인한다.
"""

from __future__ import annotations

from types import SimpleNamespace

from app.llm.factory import build_llm, get_chat_llm
from app.llm.profiles import LLMProfile


def _profile(model: str) -> LLMProfile:
    return LLMProfile(name="chat", model=model, temperature=0.8,
                      max_tokens=4000, timeout=60, max_retries=0)


def _build(model: str, monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    return build_llm(_profile(model))


def _build_with(model: str, thinking, monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    return build_llm(_profile(model), thinking)


# --- 요청이 레벨을 줄 때 -----------------------------------------------------

def test_level_becomes_effort_on_both_providers(monkeypatch):
    claude = _build_with("anthropic/claude-opus-4.8", "high", monkeypatch)
    gemini = _build_with("google/gemini-3.5-flash", "high", monkeypatch)
    assert claude.reasoning_effort == "high"
    # 이름만 같고 실제로 나가는 노브는 thinking_level이다(alias).
    assert gemini.reasoning_effort == "high"
    assert gemini.thinking_budget is None


def test_off_means_no_field_on_claude_but_budget_zero_on_gemini(monkeypatch):
    """'끔'의 표현이 제공사마다 다르다 — Claude는 필드를 안 보내는 것이 곧 끔이고,
    Gemini의 thinking_level에는 끔 값이 없어 budget=0으로만 끈다."""
    claude = _build_with("anthropic/claude-opus-4.8", "off", monkeypatch)
    gemini = _build_with("google/gemini-3.5-flash", "off", monkeypatch)
    assert claude.reasoning_effort is None
    assert gemini.reasoning_effort is None and gemini.thinking_budget == 0


def test_off_omits_the_field_when_budget_zero_is_rejected(monkeypatch):
    """끄는 방법이 모델마다 다르다 — flash-lite는 기본이 추론 없음이라 필드를 빼는 게 끔이고,
    budget=0을 보내면 오히려 400이다."""
    llm = _build_with("google/gemini-3.5-flash-lite", "off", monkeypatch)
    assert llm.thinking_budget is None and llm.reasoning_effort is None
    # 켤 때는 다른 Gemini와 같다.
    assert _build_with("google/gemini-3.5-flash-lite", "high", monkeypatch).reasoning_effort == "high"


def test_effort_is_never_sent_to_models_that_reject_it(monkeypatch):
    """haiku-4.5는 effort를 보내면 400이다. 카탈로그가 노브를 안 열어 여기까지 오지 않지만,
    요약 프로필처럼 백엔드를 안 거치는 경로가 있어 마지막 방어선을 둔다."""
    llm = _build_with("anthropic/claude-haiku-4.5", "high", monkeypatch)
    assert llm.reasoning_effort is None


def test_cache_key_includes_the_level(monkeypatch):
    """⚠️ slug만으로 캐시하면 그 방에서 처음 쓰인 레벨이 계속 재사용돼 조용히 틀린다."""
    monkeypatch.setenv("GOOGLE_API_KEY", "test-key")
    app = SimpleNamespace(
        state=SimpleNamespace(chat_llm_cache={},
                              chat_profile=_profile("google/gemini-3.5-flash")))
    low = get_chat_llm(app, "google/gemini-3.5-flash", "low")
    high = get_chat_llm(app, "google/gemini-3.5-flash", "high")
    assert low.reasoning_effort == "low" and high.reasoning_effort == "high"
    assert get_chat_llm(app, "google/gemini-3.5-flash", "low") is low


# --- 요청이 레벨을 안 줄 때(= 레벨 노출 이전 거동) ---------------------------

def test_flash_turns_thinking_off(monkeypatch):
    llm = _build("google/gemini-3.5-flash", monkeypatch)
    assert llm.thinking_budget == 0


def test_pro_preview_lowers_thinking_instead(monkeypatch):
    """끌 수는 없고(끄면 400) 낮출 수는 있는 모델. 필드 이름은 reasoning_effort지만
    실제로 나가는 노브는 alias인 thinking_level이다."""
    llm = _build("google/gemini-3.1-pro-preview", monkeypatch)
    assert llm.reasoning_effort == "low"
    assert llm.thinking_budget is None


def test_flash_lite_gets_neither(monkeypatch):
    """기본이 추론 없음이라 아무것도 안 보내는 게 맞다(budget=0을 보내면 400)."""
    llm = _build("google/gemini-3.5-flash-lite", monkeypatch)
    assert llm.thinking_budget is None
    assert llm.reasoning_effort is None


def test_sampling_is_dropped_only_for_models_that_reject_it(monkeypatch):
    """Claude 4.7↑는 temperature를 보내면 요청 자체가 죽는다(4.6은 아직 받는다)."""
    assert _build("anthropic/claude-opus-4.8", monkeypatch).temperature is None
    assert _build("anthropic/claude-opus-4.6", monkeypatch).temperature == 0.8
