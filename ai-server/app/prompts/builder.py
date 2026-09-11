from __future__ import annotations

from app.prompts.config import PromptConfig
from app.prompts.sections import (
    apply_placeholders,
    character_section,
    lore_section,
    memory_section,
    response_section,
    rules_section,
    safety_section,
    user_section,
    writing_section,
)


def build_system_parts(
    persona: str,
    lore_entries: list[str] | None = None,
    summary: str | None = None,
    config: PromptConfig | None = None,
    user_name: str | None = None,
    user_persona: str | None = None,
    char_name: str | None = None,
) -> tuple[str, str]:
    """시스템 프롬프트를 (안정 구간, 매 턴 바뀌는 나머지) 두 조각으로 돌려준다.

    경계를 여기서 정하는 건 어느 섹션이 얼마나 자주 바뀌는지를 이 모듈만 알기 때문이다.
    두 조각을 어떻게 쌀지(한 덩어리 문자열 / 캐시 표시가 붙은 블록 둘)는 제공사를 아는
    `app.llm.cache.to_system_message` 가 정한다 — 여기선 제공사를 끝까지 모른다.
    """
    config = config or PromptConfig()

    # 순서는 "안정된 것부터". 프롬프트 캐싱이 프리픽스 일치라서, 앞에서 한 글자만 바뀌어도
    # 뒤가 전부 무효가 된다. 그래서 로어를 맨 뒤에 둔다 — 로어는 이번 유저 입력의 키워드로
    # 골라 오므로(백엔드 LoreService.selectLore) 매 턴 내용이 달라지는 유일한 섹션이다.
    # 기억은 요약이 새로 생길 때만 바뀌고 덩치가 제일 크니(최대 3,500토큰) 로어 앞에 둬서
    # 캐시되는 구간 안에 들어가게 한다.
    stable = [
        rules_section(),
        safety_section(),
        writing_section(),
        response_section(config),
        character_section(persona),
        user_section(user_persona),
        memory_section(summary),
    ]
    volatile = [lore_section(lore_entries)]

    # ⚠️ 치환은 조각마다 따로 돌린다. 합친 뒤에 한 번 돌리던 걸 옮긴 것이라 결과는 같지만
    # (자리표시자가 섹션 경계를 넘지 않는다), 조각을 나눌 때 빠뜨리면 로어 안의 {{char}}가
    # 치환되지 않은 채로 나간다.
    def _join(sections: list[str]) -> str:
        return apply_placeholders(
            "\n\n".join(s for s in sections if s),
            user_name=user_name,
            char_name=char_name,
        )

    return _join(stable), _join(volatile)
