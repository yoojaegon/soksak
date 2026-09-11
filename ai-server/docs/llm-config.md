# LLM 설정 (ai-server)

> **이 문서는 `.env` 템플릿이 아니다.** `ai-server/.env` 는 만들지 않는다 — 환경변수는
> 루트 `.env`(로컬 실행)와 `docker-compose.yml`(컨테이너)이 준다. 예전엔 이 자리에
> `.env.example` 이 있었지만, 그걸 복사해 만든 `ai-server/.env` 가 루트 `.env` 보다 먼저
> 잡혀서(`load_dotenv()` 는 `app/main.py` 에서 위로 올라가며 첫 `.env` 를 찾는다) 정작
> 제공사 키를 못 보는 함정이 됐다.
>
> 여기 적는 건 **코드가 읽는 노브의 목록과 그 함정**이다. 기본값의 단일 출처는 언제나
> 코드(`app/main.py` 의 `load_profile` 호출)다.

## 제공사 키

slug 접두사로 제공사를 고르고, 그 제공사의 **네이티브 SDK**로 붙는다
(`app/llm/factory.py` 의 `_PROVIDERS`). 그래서 채팅용 키가 따로 있지 않고 제공사 키를 쓴다.

| 변수 | 비고 |
|------|------|
| `ANTHROPIC_API_KEY` | `anthropic/*` 채팅 + 요약 기본 모델 + 토큰 수 세기(`count_tokens`, 무료·별도 리밋) |
| `GOOGLE_API_KEY` | `google/*` 채팅. 채팅 기본 모델이 `google/*` 라 사실상 필수 |

기동 시 확인한다(`factory.check_api_keys`): **채팅 폴백 모델·요약 모델의 제공사 키**가 비어
있으면 기동이 실패하고, 나머지 제공사 키는 경고만 남는다(그 제공사 방의 첫 메시지에서 500).

## 선택 가능 모델

✅ 모델 카탈로그(선택지·표시명·기본값·검증)의 **단일 출처는 자바 백엔드**(`GET /models`).
ai-server는 요청에 실려 온 slug를 그대로 실행할 뿐, 목록도 기본값도 갖지 않는다.
버전 표기는 점이고, 제공사 실제 ID로의 변환(`claude-opus-4.8` → `claude-opus-4-8`)은
`factory.py` 가 한다.

| slug | 비고 |
|------|------|
| `anthropic/claude-opus-4.8` | ⚠️ temperature 미지원(보내면 400) |
| `anthropic/claude-opus-4.7` | ⚠️ temperature 미지원(보내면 400) |
| `anthropic/claude-opus-4.6` | |
| `anthropic/claude-haiku-4.5` | 저가, **요약 기본 모델** |
| `google/gemini-3.1-pro-preview` | ⚠️ 추론을 끌 수 없다(실측 544~796토큰) |
| `google/gemini-3.5-flash` | 추론이 기본 켜짐 → factory가 `thinking_budget=0` 으로 끈다 |
| `google/gemini-3.5-flash-lite` | 최저가, 추론 없음, 샘플링 고정 — **채팅 기본 모델** |

전부 두 제공사 키로 실제 호출해 확인했다(네이티브 전환 실측 2026-09-10).
추론 설정은 노브가 아니라 `factory.py` 가 slug를 보고 정한다(제공사마다 인자가 다르다).
방마다 추론 레벨을 고르게 하는 건 별도 작업.

## 튜닝 노브 (전부 선택)

평상시엔 아무것도 안 넣어도 된다. 아래는 "이런 노브가 있다"는 목록이자 실험용 스위치로,
값을 주면 코드 기본값을 덮어쓴다. **실험이 끝나면 지울 것** — 값을 상주시키면 코드와 두
곳으로 갈라져 어느 쪽이 도는지 헷갈린다.

```bash
# 모델명은 provider/model 형식. CHAT_MODEL은 요청에 model이 안 실려 왔을 때의
# 폴백(개발/전환기용) — 평상시엔 백엔드가 항상 보낸다.
CHAT_MODEL=google/gemini-3.5-flash-lite
CHAT_TEMPERATURE=0.8
CHAT_MAX_TOKENS=20000
CHAT_TIMEOUT=60
CHAT_MAX_RETRIES=0

# 요약은 SUMMARY_ 고정 프로필(방별 모델 선택은 채팅 전용).
SUMMARY_MODEL=anthropic/claude-haiku-4.5
SUMMARY_TEMPERATURE=0.3
SUMMARY_MAX_TOKENS=2000
SUMMARY_TIMEOUT=60
SUMMARY_MAX_RETRIES=0

# 토큰 수 세기의 기준 토크나이저.
TOKEN_COUNT_MODEL=claude-opus-4-8
```

- ⚠️ **요약 모델은 thinking이 꺼진 것으로 고를 것** — 추론 토큰이 `max_tokens` 에서 나가므로
  thinking 모델을 물리면 2000을 추론이 다 먹고 요약문이 빈 채로 돌아온다(증상이 안 보인다).
- `TIMEOUT` 은 혼자 못 움직인다 — 백엔드 `application.yml` 의 `ai-server.read-timeout`(60초)과
  한 쌍이라, 백엔드가 더 짧으면 여기를 올려도 아무 효과가 없다(백엔드가 먼저 포기).
- `MAX_RETRIES` 도 같은 예산에 묶인다: `timeout × (재시도+1)` 이 read-timeout을 넘으면
  초과분은 아무도 안 듣는 유료 호출이라 0이다. 올리려면 `TIMEOUT` 부터 내릴 것.

### ⚠️ 노브를 실제로 먹이는 경로가 둘이고, 서로 다르다

| 실행 방식 | 어디에 쓰나 | 먹는 노브 |
|-----------|-------------|-----------|
| 로컬 `uvicorn` | 루트 `.env` | **전부** (`load_dotenv()` 가 파일 전체를 읽는다) |
| 컨테이너 | `docker-compose.yml` 의 `ai-server.environment` | **거기 적힌 것만** |

컨테이너에는 `.env` 파일이 들어가지 않는다(Dockerfile은 `pyproject.toml`/`uv.lock`/`app` 만
복사한다). compose가 지금 넘기는 건 이것뿐이다:

`INTERNAL_AUTH_SECRET`, `ANTHROPIC_API_KEY`, `GOOGLE_API_KEY`,
`SUMMARY_MODEL`, `SUMMARY_TIMEOUT`, `TOKEN_COUNT_MODEL`

즉 `CHAT_TEMPERATURE` 같은 걸 루트 `.env` 에 적어도 **컨테이너에서는 아무 일도 일어나지
않는다.** 컨테이너에서 실험하려면 `docker-compose.yml` 에 그 줄을 먼저 추가해야 한다.

## 프롬프트 캐싱

캐릭터 채팅은 시스템 프롬프트(지침 + 페르소나 + 기억)가 턴마다 거의 그대로 반복되는,
캐싱이 가장 잘 듣는 모양이다. **`anthropic/*` 방에서만 켠다** — Gemini는 암시적 캐싱이 기본
on이라 켤 게 없고, 명시적 캐싱(`CachedContent`)은 저장 시간당 요금이 붙어 이 규모엔 손해다.

**어디서 잘리나.** 캐싱은 프리픽스 일치라 앞에서 한 글자만 바뀌어도 뒤가 전부 무효다.
그래서 시스템 프롬프트를 `<lore>` 앞에서 두 조각으로 나눈다:

| 조각 | 내용 | 언제 바뀌나 |
|------|------|-------------|
| **안정**(캐시됨) | `<rules>`~`<memory>` | 방 설정·요약이 갱신될 때만 |
| **변동** | `<lore>` | **매 턴** — 이번 입력의 키워드로 골라 온다(`LoreService.selectLore`) |

`builder.build_system_parts` 가 경계만 정하고, `llm/cache.py` 의 `to_system_message` 가
제공사에 맞게 싼다(문자열 하나 / `cache_control` 이 붙은 블록 둘). 캐싱 on/off로 프롬프트
**내용**은 달라지지 않는다 — 모양만 다르다.

⚠️ **최소 토큰 미달이면 에러 없이 그냥 캐시가 안 된다.** 문턱은 **캐시되는 구간에만** 걸리고
(로어는 뒤라 보태지지 않는다) 세대순도 아니다:

| 모델 | 최소 | 고정 지침 967토큰 위에 더 필요한 양 |
|------|-----:|---|
| `claude-opus-4.8` | 1,024 | ~57 — 사실상 항상 걸린다 |
| `claude-opus-4.7` | 2,048 | ~1,081 |
| `claude-opus-4.6` · `claude-haiku-4.5` | 4,096 | ~3,129 — 기억(최대 3,500)이 있어야 넘는다 |

⚠️ **TTL은 5분(기본).** 1시간짜리는 쓰기가 2배다. 손익분기는 5분=2회·1시간=3회 읽기고,
수명은 요청 **시작** 시각부터 잰다. 캐릭터챗은 사람이 읽고 타이핑하는 간격이라 5분을
넘나드니, 실제 턴 간격을 아래 usage 로그로 보고 나서 바꿀 것.

⚠️ **breakpoint는 시스템 블록 하나뿐이다.** 대화 이력에는 안 찍는다 — 백엔드
`HistoryTrimmer` 가 앞을 자르는 슬라이딩 윈도이고 요약 커서도 전진해서 messages 프리픽스가
매 턴 깨진다. 거기 찍으면 쓰기 프리미엄만 내고 읽기는 0이다.

## 토큰 사용량 로그 (캐싱이 도는지 보는 창)

채팅 한 턴마다 `app.llm.usage` 가 한 줄을 남긴다:

```
토큰 model=claude-opus-4-8 입력=5000(캐시읽기=4000 캐시쓰기=500 미캐시=500) 출력=300(추론=0)
```

- **`캐시읽기`** — 이게 0에서 안 움직이면 캐싱이 안 되고 있는 것이다. 프롬프트 캐싱은
  **실패해도 조용하다**(요청은 성공하고 요금만 오른다). 프롬프트 조립을 건드린 뒤엔 항상 여기를 볼 것.
- **`캐시쓰기`** — Anthropic 명시적 캐싱에만 있다. Gemini의 암시적 캐싱은 쓰기 개념이 없어 늘 0이고
  `캐시읽기`만 움직인다. 둘 다 `input_token_details.cache_read` 로 정규화돼 오므로 같은 줄로 본다.
- **`추론`** — `output_tokens` 의 부분집합이다(추가분이 아니다). `gemini-3.1-pro-preview` 가 실제로
  얼마나 추론에 쓰는지 여기서 보인다.
- ⚠️ **`입력` 은 전체 입력이다** — 벤더 원본과 의미가 다르다. Anthropic 원본의 `input_tokens` 는
  '캐시를 뺀 나머지'지만 LangChain이 캐시분을 도로 더해 넣는다. 그래서 `미캐시` 는 빼서 구한 값이다.
- 스트리밍은 사용량이 청크 여러 개에 나뉘어 오므로(입력=첫 청크, 출력=마지막) 더해서 집계한다.
  클라이언트가 중간에 끊어도 그때까지 쓴 만큼은 남는다.

## 내부 인증

`INTERNAL_AUTH_SECRET` — 백엔드와 공유하는 정적 시크릿. 백엔드가 `X-Internal-Auth` 헤더로
보내고 ai-server가 대조한다(불일치/누락 시 401). 루트 `.env` 한 곳에만 두고 compose가
양쪽 컨테이너에 넣어준다. 예: `openssl rand -hex 32`
