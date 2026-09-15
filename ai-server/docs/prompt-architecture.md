# 프롬프트 아키텍처 설계 (ai-server)

> 상태: **설계안 / 검토 대기** — 구현 전 합의용 문서.
> 목적: 현재 평평한 시스템 프롬프트를, 캐릭터챗에 맞는 **계층형(섹션) 프롬프트 조립** 구조로 승격한다.

## 0. 설계 원칙

모든 프롬프트 문구는 직접 작성한다. 외부 캐릭터챗 도구들의 일반적인 프롬프트 구성
방식을 일반론으로 참고하되, 특정 프리셋의 원문을 가져오지 않는다.

## 1. 현재 상태

`app/prompts/builder.py` 의 조립 함수는 사실상 아래를 한 덩어리로 이어붙였다:

```
persona + (lore_entries) + (summary)  ->  단일 SystemMessage
```

`app/chains/chat.py` 는 `[system, *history, Human(user_text)]` 로 호출한다.
역할 분리·서술 규칙·출력 형식·모드 제어가 전혀 없다.

## 2. 목표 구조

시스템 프롬프트를 **고정 순서의 섹션**으로 조립한다. 섹션 구분은 모델 파싱이
잘 되도록 태그형(`<rules>`, `<writing>`, `<character>`, `<user>`, `<memory>`, `<lore>`)을 쓴다.

```
[시스템 프롬프트]
  1. <rules>      역할/정체성 + 코어 하드룰 (메타금지·일관성·시점·반복회피·언어) (항상)
  2. <writing>    작문 지침: 도입·대사·보여주기·갈등/긴장·완급 (항상)
  3. <response>   모드 지침(rp | writing) + 스포일러 지침(fold_spoilers=true 일 때만)
  4. <character>  persona (항상)
  5. <user>       user_persona (있을 때)
  6. <memory>     발췌 고지 + summary (있을 때)
  7. <lore>       lore_entries (있을 때)
[이후 메시지]
  *history + Human(user_text)
```

**순서는 "안정된 것부터"라는 규칙 하나로 정해진다.** 프롬프트 캐싱이 프리픽스 일치라
앞에서 한 글자만 바뀌어도 뒤가 전부 무효가 된다. `<lore>` 가 맨 뒤인 건 이번 유저 입력의
키워드로 골라 오는(`LoreService.selectLore`) **매 턴 달라지는 유일한 섹션**이기 때문이고,
`<memory>` 가 그 앞인 건 요약이 새로 생길 때만 바뀌면서 덩치는 제일 커서(최대 3,500토큰)
캐시되는 구간 안에 두는 편이 이득이기 때문이다. 섹션을 추가할 땐 이 축으로 자리를 잡을 것.

조립이 끝난 시스템 프롬프트에 **자리표시자 치환**을 적용한다(조각마다 한 번씩 — §4):
`{{user}}` → `user_name`(없으면 "유저"), `{{char}}` → `char_name`(없으면 "캐릭터").
공백·대소문자 변형(`{{ User }}` 등)도 함께 치환한다. 우리가 작성한 지침 텍스트뿐
아니라 백엔드가 보낸 `persona`/`user_persona`/`lore` 안의 토큰도 같이 처리된다.

## 3. 토글 / 설정 모델

사용자가 제어하는 토글은 **딱 2개**(`mode`, `fold_spoilers`)뿐이다. 그 외 부가 기능
토글은 두지 않는다. 토글과 무관한 **base 프롬프트는 항상 포함**한다.

### 3.1 PromptConfig (신규)

```python
class PromptMode(str, Enum):
    RP = "rp"            # 기본모드
    WRITING = "writing"  # 풀사칭모드

class PromptConfig(BaseModel):
    mode: PromptMode = PromptMode.RP
    fold_spoilers: bool = False
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

### 3.3 스포일러 접기 토글 (`fold_spoilers`)

유저 시점에서 알 수 없는 내용(다른 캐릭터의 내면, 유저가 없는 장소의 사건 등)을 AI가
묘사하는 일이 잦은데, 그걸 본문에 그대로 노출하면 어색하다. 이 토글은 그런 내용을
**억제하는 게 아니라, 생성하되 접이식(스포일러)으로 감싸** 화면에서 기본 접힘 →
유저가 클릭해 펼치게 한다. (이름이 `hide_`가 아니라 `fold_`인 이유: 숨기는 게 아니라
생성해서 접어두는 동작이기 때문.)

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
- **반복 회피** (권장): 동일 표현·문장 구조 반복 억제.
- **언어 일치** (권장): 유저 언어(한국어)로 응답.
- 안전·수위: **보류** (§9).

> **유저 주도권 존중**은 `<rules>` 가 아니라 `<response>` 모드 섹션에서 다룬다. 항상 켜진
> 규칙으로 두면 풀사칭(`writing`)모드와 충돌하므로, 모드별 문구(`_MODE_RP`/`_MODE_WRITING`)로
> 분리했다.

## 4. 모듈 구성 (제안)

```
app/prompts/
  config.py     # PromptConfig, PromptMode
  sections.py   # 섹션별 텍스트 생성 함수 (순수 함수, 원문 작성)
  builder.py    # 섹션 순서 조립 -> (안정 구간, 변동 구간) 두 조각
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

`builder.build_system_parts(persona, lore_entries, summary, config, user_name,
user_persona, char_name)` 가 위 섹션을 순서대로 합친 뒤 `apply_placeholders` 로
자리표시자를 치환해 **`(안정 구간, 매 턴 바뀌는 나머지)` 두 조각**을 돌려준다.
경계는 `<lore>` 앞이다(§2의 "순서는 안정된 것부터").

⚠️ 치환은 **조각마다 따로** 돈다. 합친 뒤 한 번 돌리던 걸 옮긴 것이라 결과는 같지만
(자리표시자가 섹션 경계를 넘지 않는다), 빠뜨리면 로어 안 `{{char}}` 가 그대로 나간다.

두 조각을 실제 `SystemMessage` 로 싸는 건 `app/llm/cache.py` 의 `to_system_message` 다 —
제공사에 따라 한 덩어리 문자열이거나, 앞 조각에 `cache_control` 이 붙은 블록 둘이다.
`builder` 는 경계만 알고 제공사는 끝까지 모른다(자세한 건 `docs/llm-config.md`).

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
     -> build_system_parts(persona, lore, summary, config, user_name, user_persona, char_name)
        -> 안정: [rules][writing][response(mode,spoiler)][character][user][memory]
           변동: [lore]                       # 매 턴 키워드로 골라 오므로 경계가 여기
        -> apply_placeholders({{user}},{{char}})  조각마다 => (stable, volatile)
     -> to_system_message(stable, volatile, cacheable)  => SystemMessage
     -> [SystemMessage, *history, Human(user_text)]
     -> llm.invoke / stream
```

`summarizer.py` 는 이 흐름과 별개다 — §10 참고.

## 7. 구현 단계

1. **섹션화** ✅ 완료: `config.py` + `sections.py` 신규, `builder.py` 재작성. base 프롬프트 원문 작성.
2. **토글 연결 (ai-server)** ✅ 완료: `PromptConfig` 를 `ChatRequest`/`chat()`/`chat_stream()` 에 배선,
   모드·스포일러 지침 활성화. `config` 기본값(rp/스포일러off)이라 하위호환.
   - ⏳ 남음(백엔드/프론트): 백엔드가 `config` 를 채팅방 단위로 저장·전달, 프론트가 토글 UI +
     `<spoiler>` 접이식 렌더.
3. **`<writing>` 작문 지침** ✅ 완료: 도입·대사·보여주기·갈등/긴장·완급 추가(직유우선 제거).
4. **`{{user}}` 처리 / `<user>` 섹션** ✅ 완료: `user_name`/`user_persona`/`char_name` 를
   `ChatRequest`/`chat()`/`chat_stream()`/`build_system_parts()` 에 배선. `<user>` 섹션
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
3. **base `<rules>` 범위 = 필수 3 + 권장 2.**
   - 필수: 메타발언 금지 / 캐릭터 일관성 / 서술 시점 고정
   - 권장: 반복 회피 / 언어 일치(한국어)
   - 유저 주도권 존중은 `<rules>` 가 아니라 `<response>` 모드 섹션에서 처리(§3.5 비고).
   - **안전·수위 정책은 보류** — 별도 정책 결정 후 추가(현재 base 에 미포함).

## 9. 미결 / 추후

- 안전·수위 정책 문구 (정책 확정 후 `<rules>` 또는 별도 섹션에 추가).

## 10. 요약 파이프라인 (`app/memory/`)

시스템 프롬프트 조립과는 별개 경로다. `<memory>` 섹션에 **무엇을 넣을지**를 만드는 쪽이고,
넣는 방식은 §2 그대로다.

백엔드는 쌓아 둔 요약 조각 중 예산에 맞는 것만 골라 보내므로 기록에는 구멍이 뚫린다. 그래서
`memory_section` 은 요약 본문 앞에 **"이 기록은 발췌"** 고지(`_MEMORY_NOTE`)를 상시 붙인다 —
없으면 모델이 빠진 구간을 "아무 일도 없었다"로 추론한다.

### 10.1 갱신형 → 구간형

기존 `summarizer.update(existing_summary, new_turns)` 는 기존 요약을 통째로 다시 쓰는
**갱신형**이었다. 이 방식은 요약할 때마다 과거 전체가 모델을 한 번 더 통과해서, 세션이
길어질수록 초반 사실이 조용히 마모된다("요약의 요약").

`summarizer.summarize(previous_summaries, new_turns)` 는 넘겨받은 **구간 하나만** 독립된
기록으로 만든다. 만든 요약은 다시 쓰지 않는다. 누적은 **백엔드가 요약을 여러 건 쌓아 두고
매 턴 그중 일부만 골라 보내는** 방식으로 처리한다(선택·예산 계산은 백엔드 소유).

`previous_summaries` 는 대명사·생략된 주어를 해석하기 위한 **맥락일 뿐**이며, 프롬프트에
"다시 옮겨 적지 말라"는 지시와 함께 들어간다.

### 10.2 출력 형식

자유 서술 대신 고정 슬롯을 채우게 한다 — 지시는 지켜질 수도 아닐 수도 있지만 슬롯은
비어 있으면 눈에 띈다.

```
시점·장소 / 상황 / 사건(최대 6) / 인물(심리·관계 변화) / 대사(최대 6) / 미해결
```

⚠️ **이 명세(`_FORMAT`)는 프롬프트와 `summary` 필드 설명 양쪽에 실린다.** 프롬프트에만 두면
모델이 형식을 통째로 버린다. 문구가 갈리면 한쪽만 고쳐졌을 때 다시 흔들리므로 **반드시
`_FORMAT` 하나에서 파생**시킬 것(`_SUMMARY_FIELD`).

> 🔬 2026-09-14 실측(claude-haiku-4.5, 같은 구간 반복). 형식을 프롬프트에만 두면
> `[앞선 기록]` 이 **1건**일 때 12회 중 **6회** 붕괴했다. 깨질 땐 슬롯이 부분이 아니라 0개가
> 되고 길이도 1/3로 줄어 대사·미해결이 통째로 사라진다. 앞선 기록이 없거나(0/4) 2건일
> 때(0/9)는 안 깨졌는데, 하필 **1건인 경우가 `SummaryPlan.CONTEXT_SUMMARIES=2` 아래에서
> 방마다 꼭 한 번(seq=2) 온다** — 그 앞엔 조각이 하나뿐이라 개수를 바꿔도 못 피한다.
> 같은 명세를 필드 설명에 얹자 **0/24**. 구조화 출력에선 필드 설명이 프롬프트보다 세게 먹는다.

⛔ **슬롯을 필드로 쪼개는 방식은 실제로 해봤다가 되돌렸다. 다시 시도하지 말 것.**
`SummaryDraft` 로 칸마다 필드를 두고 머리말·불릿을 코드가 붙이는 설계였는데, 필드가 늘자
모델이 툴콜의 **XML 방언**(`<item>…</item>`, `<parameter name="...">`, `</invoke>`)으로 새기
시작했다. 🔬 같은 프롬프트 12회 기준:
>
> - 목록 칸을 `list[str]` 3개로 → **파싱 실패 4/12**. 배열을 뱉다 방언으로 새고 거기서 응답이
>   끊겨 뒤쪽 칸이 통째로 유실된다(`stop_reason=tool_use` 인데 인자가 반만 온다).
> - 줄바꿈 문자열로 바꾸면 파싱은 0/12이지만, 이번엔 **값 안에서** 새서
>   `</unresolved><parameter name="importance">` 같은 조각이 본문에 섞여 **조용히 저장**된다
>   (폴백도 안 걸린다). 구분자도 불안정해서 어떤 응답은 개행을 글자 그대로 `\n` 두 글자로,
>   어떤 응답은 ` / ` 로 준다.
> - 문자열 하나짜리 스키마(현재)는 세 지표 모두 0/24.

`keywords` 는 짧고 마지막이라 배열로 둬도 실패가 없었다 — 배열 자체가 아니라 **필드 수와
값의 길이**가 방아쇠다.

여기에 검색·선택용 메타데이터가 붙는다:

| 필드 | 용도 |
|------|------|
| `importance` (1~5) | 오래된 요약이라도 무게가 있으면 계속 프롬프트에 싣기 위한 값. 프롬프트에 기준표와 "대부분 2~3" 경고를 함께 준다 — 없으면 모델이 죄다 4~5를 매긴다 |
| `keywords` (3~8) | 나중에 이번 입력과 관련된 옛 요약을 끌어올 때 쓸 검색어 |

`with_structured_output` 으로 받되, 실패하면(구조화 출력의 구현이 제공사마다 달라 요약 모델을
바꾸면 안 될 수 있다) **평문 요약으로 폴백**한다. 메타데이터는 잃어도 요약 자체는 살린다.
폴백은 같은 프롬프트를 그대로 쓰므로 형식 안내는 그쪽에도 실려 있다.

### 10.3 토큰 수 (`token_counter.py`)

요약을 만든 자리에서 그 길이를 재서 `token_count` 로 함께 넘긴다. 백엔드가 "어느 요약까지
실을지"를 예산으로 판단하는 데 쓰며, 요약은 내용이 안 바뀌므로 **생성 시점에 한 번만** 재면
이후엔 정수 덧셈으로 끝난다.

- 채팅과 같은 `ANTHROPIC_API_KEY` 를 쓰지만 호출은 별개다(무료, 레이트 리밋도 별개).
  LangChain을 거치지 않고 `anthropic` SDK를 직접 쓴다 — 생성이 아니라 측정이라 채팅
  프로필과 공유할 설정이 없다.
- 기준 모델 하나(`TOKEN_COUNT_MODEL`, 기본 `claude-opus-4-8`)로만 잰다. 모델별로 재두면
  카탈로그가 바뀔 때마다 과거 요약을 다시 세야 하는데, **과소추정만 사고(컨텍스트 초과)를
  내고 과대추정은 무해**하므로 가장 무거운 토크나이저 하나로 재서 나머지는 넉넉히 잡히게 둔다.
- 측정 실패는 전부 삼키고 근사식(`글자수 × 1.15 + 10`)으로 대체한다. 토큰 수는 보조값이지
  요약의 일부가 아니라, 여기서 실패가 올라가면 측정 실패가 요약 실패가 되어버린다.

> 근사식 계수 근거(2026-09-01 실측, `claude-opus-4-8`, 한국어): 본문 **글자당 1.12토큰**,
> 메시지당 고정 오버헤드 **7토큰**. 한글은 영어처럼 압축되지 않아 대략 *1글자 ≈ 1토큰* 이다.
> 참고로 `tiktoken`(o200k)은 같은 한국어 텍스트에서 Claude 값의 **0.6배**밖에 안 나와
> 기준 단위로 쓸 수 없다.

### 10.4 API 계약

```
POST /summarize
  { previous_summaries: [...], new_messages: [...] }
  → { summary, importance, keywords, token_count }
```

응답의 `summary` 키는 갱신형 시절과 이름이 같다 — 구간형에서도 그대로 쓰는 키다.
