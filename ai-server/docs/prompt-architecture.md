# 프롬프트 아키텍처 설계 (ai-server)

> 상태: **설계안 / 검토 대기** — 구현 전 합의용 문서.
> 목적: 현재 평평한 시스템 프롬프트를, 캐릭터챗에 맞는 **계층형(섹션) 프롬프트 조립** 구조로 승격한다.

## 0. 설계 원칙

모든 프롬프트 문구는 직접 작성한다. 외부 캐릭터챗 도구들의 일반적인 프롬프트 구성
방식을 일반론으로 참고하되, 특정 프리셋의 원문을 가져오지 않는다.

## 1. 현재 상태

`app/prompts/builder.py` 의 `build_system_message()` 는 사실상 아래를 한 덩어리로 이어붙인다:

```
persona + (lore_entries) + (summary)  ->  단일 SystemMessage
```

`app/chains/chat.py` 는 `[system, *history, Human(user_text)]` 로 호출한다.
역할 분리·서술 규칙·출력 형식·모드 제어가 전혀 없다.

## 2. 목표 구조

시스템 프롬프트를 **고정 순서의 섹션**으로 조립한다. 섹션 구분은 모델 파싱이
잘 되도록 태그형(`<rules>`, `<writing>`, `<character>`, `<user>`, `<lore>`, `<memory>`)을 쓴다.

```
[시스템 프롬프트]
  1. <rules>      역할/정체성 + 코어 하드룰 (메타금지·일관성·시점·반복회피·언어) (항상)
  2. <writing>    작문 지침: 도입·대사·보여주기·갈등/긴장·완급 (항상)
  3. <response>   모드 지침(rp | writing) + 스포일러 지침(hide_spoilers=true 일 때만)
  4. <character>  persona (항상)
  5. <user>       user_persona (있을 때)
  6. <lore>       lore_entries (있을 때)
  7. <memory>     summary (있을 때)
[이후 메시지]
  *history + Human(user_text)
```

조립이 끝난 시스템 프롬프트 전체에 **자리표시자 치환**을 한 번 적용한다:
`{{user}}` → `user_name`(없으면 "유저"), `{{char}}` → `char_name`(없으면 "캐릭터").
공백·대소문자 변형(`{{ User }}` 등)도 함께 치환한다. 우리가 작성한 지침 텍스트뿐
아니라 백엔드가 보낸 `persona`/`user_persona`/`lore` 안의 토큰도 같이 처리된다.

## 3. 토글 / 설정 모델

사용자가 제어하는 토글은 **딱 2개**(`mode`, `hide_spoilers`)뿐이다. 그 외 부가 기능
토글은 두지 않는다. 토글과 무관한 **base 프롬프트는 항상 포함**한다.

### 3.1 PromptConfig (신규)

```python
class PromptMode(str, Enum):
    RP = "rp"            # 기본모드
    WRITING = "writing"  # 풀사칭모드

class PromptConfig(BaseModel):
    mode: PromptMode = PromptMode.RP
    hide_spoilers: bool = False
    # 작문 지침(<writing>)은 토글이 아니라 항상 적용되는 상수다.
```

### 3.2 모드 토글 (`mode`)

| 값 | 이름 | 동작 (새로 작성할 지침의 의도) |
|----|------|-------------------------------|
| `rp` | 기본모드 | 유저 인풋의 대사·행동은 이야기에 **반영**하되, 그 **이상으로 {{user}}를 사칭해 대사·행동을 만들지 않는다.** |
| `writing` | 풀사칭모드 | 인풋이 "다음 진행해줘" 수준으로 짧을 때, AI가 상상력을 더해 **{{user}} 캐릭터의 대사·행동까지 주도적으로 묘사**하며 플롯을 이끈다. |

> 선택 근거: 기본모드는 "유저 인풋을 반영하되 그 이상은 사칭하지 않는다"는 정의로,
> 평소 채팅에서 유저 주도권을 지키는 기본값에 맞다. 풀사칭모드는 유저가 전개를 위임할
> 때를 위한 보완 모드로, AI가 일관되게 강하게 장면을 주도하도록 한다.

### 3.3 히든 스포일러 토글 (`hide_spoilers`)

유저 시점에서 알 수 없는 내용(다른 캐릭터의 내면, 유저가 없는 장소의 사건 등)을 AI가
묘사하는 일이 잦은데, 그걸 본문에 그대로 노출하면 어색하다. 이 토글은 그런 내용을
**억제하는 게 아니라, 생성하되 접이식(스포일러)으로 감싸** 화면에서 기본 접힘 →
유저가 클릭해 펼치게 한다.

- `false`(기본): 별도 처리 없음(평소대로 본문에 서술).
- `true`: 위 "유저가 알 수 없는 내용"을 **`<spoiler>...</spoiler>` 마크업으로 감싸** 출력.

> **크로스 레이어 의존**: 이 토글은 프롬프트가 약속된 마크업(`<spoiler>`)을 내보내고,
> **프론트가 그 마크업을 접이식 UI로 렌더**해야 완성된다. 마크업 형식은 프론트 파서와
> 맞춰 확정해야 한다(§9).

### 3.4 항상 적용 — `<writing>` 작문 지침 (상수, 토글 아님)

토글과 무관하게 항상 들어가는 작문 축. (글쓰기 일반 베스트프랙티스 — 직접 작성)

- **도입(hooking)**: 밋밋한 시작 대신 첫 문장/장면으로 흥미를 끌고 긴장 요소를 빨리 제시.
- **대사(dialogue)**: 성격·관계·상황을 설명이 아닌 사실적 대사로 드러냄.
- **보여주기(show, don't tell)**: 단정 진술 대신 행동·감각·정황으로. 장면·소품·심리 묘사 풍부히.
- **갈등과 긴장(conflict & tension)**: 장애물/변수를 두고 전개에 따라 위기감을 끌어올림.
- **완급(pacing)**: 사건과 묘사·설명의 비중을 조절해 일정한 리듬 유지.

> 시점(POV)은 §3.5 `<rules>` 의 "서술 시점 고정"으로 다룸(중복 제거).

### 3.5 base `<rules>` 코어 룰

토글과 무관하게 항상 들어가는 기본 규칙. (문구는 원문 작성)

- **메타발언 금지** (필수): "나는 AI다", 시스템/프롬프트 언급, 4의 벽 깨기 금지.
- **캐릭터 일관성** (필수): 설정된 성격·말투·지식 범위 유지, 임의 OOC 이탈 금지.
- **서술 시점 고정** (필수): 정해진 시점 유지.
- **유저 주도권 존중** (권장): {{user}} 의 의사·행동을 임의로 확정하지 않음(모드 토글과 연동).
- **반복 회피** (권장): 동일 표현·문장 구조 반복 억제.
- **언어 일치** (권장): 유저 언어(한국어)로 응답.
- 안전·수위: **보류** (§9).

## 4. 모듈 구성 (제안)

```
app/prompts/
  config.py     # PromptConfig, PromptMode
  sections.py   # 섹션별 텍스트 생성 함수 (순수 함수, 원문 작성)
  builder.py    # 섹션 순서 조립 -> SystemMessage
```

`sections.py` 예 (시그니처만):

```python
def rules_section() -> str: ...
def writing_section() -> str: ...                      # 작문 지침 상수
def response_section(config: PromptConfig) -> str: ... # 모드 + 스포일러
def character_section(persona: str) -> str: ...
def user_section(user_persona: str | None) -> str: ... # 유저 페르소나
def lore_section(lore_entries: list[str]) -> str: ...
def memory_section(summary: str) -> str: ...
def apply_placeholders(text, user_name, char_name) -> str: ...  # {{user}}/{{char}} 치환
```

`builder.build_system_message(persona, lore_entries, summary, config, user_name,
user_persona, char_name)` 가 위 섹션을 순서대로 합친 뒤 `apply_placeholders` 로
자리표시자를 치환해 `SystemMessage` 를 만든다.

## 5. API 계약 변경

`app/api/chat.py` 의 `ChatRequest` 에 선택 필드 추가:

```python
class ChatRequest(BaseModel):
    persona: str
    user_message: str
    recent_messages: list[Message] = []
    lore_entries: list[str] = []
    summary: str | None = None
    config: PromptConfig = PromptConfig()   # 토글, 기본값 있음
    char_name: str | None = None            # {{char}} 치환용
    user_name: str | None = None            # {{user}} 치환용
    user_persona: str | None = None         # <user> 섹션 본문
```

- 기본값이 있어 **하위호환**: Java 백엔드가 `config`/이름/페르소나를 안 보내도 동작
  (= rp / 스포일러off, `{{user}}`→"유저" / `{{char}}`→"캐릭터", `<user>` 생략).
- 단, 토글·유저 페르소나를 실제로 노출하려면 **Java 백엔드 호출부에서 전달**을 추가해야 함
  (별도 작업, 본 문서 범위 밖).
- `chat()` / `chat_stream()` 시그니처에 `config` + `user_name`/`user_persona`/`char_name` 추가.

## 6. 데이터 흐름

```
ChatRequest(config, user_name, user_persona, char_name)
  -> chat(llm, persona, user_text, recent, lore, summary, config, user_name, user_persona, char_name)
     -> build_system_message(persona, lore, summary, config, user_name, user_persona, char_name)
        -> [rules][writing][response(mode,spoiler)][character][user][lore][memory]
        -> apply_placeholders({{user}},{{char}})  => SystemMessage
     -> [SystemMessage, *history, Human(user_text)]
     -> llm.invoke / stream
```

`summarizer.py` 는 변경 없음.

## 7. 구현 단계

1. **섹션화** ✅ 완료: `config.py` + `sections.py` 신규, `builder.py` 재작성. base 프롬프트 원문 작성.
2. **토글 연결 (ai-server)** ✅ 완료: `PromptConfig` 를 `ChatRequest`/`chat()`/`chat_stream()` 에 배선,
   모드·스포일러 지침 활성화. `config` 기본값(rp/스포일러off)이라 하위호환.
   - ⏳ 남음(백엔드/프론트): 백엔드가 `config` 를 채팅방 단위로 저장·전달, 프론트가 토글 UI +
     `<spoiler>` 접이식 렌더.
3. **`<writing>` 작문 지침** ✅ 완료: 도입·대사·보여주기·갈등/긴장·완급 추가(직유우선 제거).
4. **`{{user}}` 처리 / `<user>` 섹션** ✅ 완료: `user_name`/`user_persona`/`char_name` 를
   `ChatRequest`/`chat()`/`chat_stream()`/`build_system_message()` 에 배선. `<user>` 섹션
   추가, 조립 후 `apply_placeholders` 로 `{{user}}`→이름·`{{char}}`→이름 치환(없으면 일반명칭).
   - ⏳ 남음(백엔드/프론트): 백엔드가 유저 페르소나·이름을 채팅방/유저 단위로 저장·전달,
     프론트가 유저 페르소나 입력 UI 제공.

## 8. 확정 사항

1. **config 출처 = 요청마다.** ai-server 는 **stateless** 를 유지하고 `config` 를 매 요청으로
   받는다. 값의 **저장·소유는 백엔드**(채팅방 단위, 예: `chatroom.chat_config`)가 맡아 매
   `/chat` 호출 때 현재 값을 실어 보낸다. 유저가 매번 안 바꿔도 백엔드가 같은 값을 재전송할 뿐.
   → ai-server 구현에는 영향 없음(저장 위치는 백엔드 별도 작업).
2. **섹션 구분자 = 태그형** (`<character>...</character>`). 외부 LLM(Claude/Gemini)이 XML형
   경계를 가장 안정적으로 따르고 섹션 번짐이 적다. 마크다운 헤더/구분선은 사용하지 않음.
3. **base `<rules>` 범위 = 필수 3 + 권장 3.**
   - 필수: 메타발언 금지 / 캐릭터 일관성 / 서술 시점 고정
   - 권장: 유저 주도권 존중 / 반복 회피 / 언어 일치(한국어)
   - **안전·수위 정책은 보류** — 별도 정책 결정 후 추가(현재 base 에 미포함).

## 9. 미결 / 추후

- 안전·수위 정책 문구 (정책 확정 후 `<rules>` 또는 별도 섹션에 추가).
- config 저장 위치 — 채팅방 단위 vs 캐릭터 단위 (백엔드 작업, ai-server 무관).
- **스포일러 마크업 형식 확정** — `<spoiler>...</spoiler>` vs `||...||` 등. 프론트 접이식
  렌더러와 한 쌍으로 정해야 함(프론트 작업 동반).
