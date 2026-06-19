from __future__ import annotations

from enum import Enum

from pydantic import BaseModel


class PromptMode(str, Enum):
    """응답 시 {{user}} 사칭 정도를 결정하는 모드."""

    RP = "rp"  # 기본모드: 유저 인풋만 반영, 그 이상 사칭 금지
    WRITING = "writing"  # 풀사칭모드: 유저 캐릭터의 대사·행동까지 주도적으로 묘사


class PromptConfig(BaseModel):
    """요청마다 전달되는 프롬프트 조립 설정 (ai-server 는 저장하지 않음)."""

    mode: PromptMode = PromptMode.RP
    hide_spoilers: bool = False
    # show_dont_tell / 직유우선 은 토글이 아니라 항상 적용되는 상수 -> sections.py 참고.
