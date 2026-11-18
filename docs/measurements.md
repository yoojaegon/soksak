# 실측 기록

코드에 박힌 상수와 규칙 중 직접 재 보고 정한 것들의 근거다.
코드 주석에는 "왜 이 값인지"만 남기고, 측정 날짜·모델·수치·방법은 여기에 모은다.
다시 재서 값이 바뀌면 이 문서와 해당 상수를 같이 고친다.

## 1. 한국어 토큰 근사식

- 측정: 2026-09-01, Anthropic `count_tokens`, 기준 모델 `claude-opus-4-8`, 한국어 대화.
- 결과: 본문 글자당 **1.12토큰**, 메시지 한 건당 고정 오버헤드 **7토큰**.
  200자 한국어는 230토큰이었다. 영어는 글자당 0.35 수준이다.
- 코드 값: 글자당 **1.15**, 오버헤드 **10**. 적게 세면 컨텍스트를 넘길 수 있어서 둘 다 올려 잡았다.
  영어에는 크게 과대추정이 되지만, 그 방향은 안전해서 그대로 둔다.
- 쓰는 곳 (세 곳이 같은 값을 써야 한다):
  - `backend/.../aiClient/HistoryTrimmer.java` (최근 대화 자르기)
  - `backend/.../chatRoom/chatSummary/SummarySelector.java` (요약 조각 고르기)
  - `ai-server/app/memory/token_counter.py` (토큰 수 세기 실패 시 근사치)

## 2. 모델별 샘플링 파라미터

- 측정: 2026-09-10, 제공사 네이티브 SDK로 `temperature`를 실어 호출.
- 결과:

| 모델 | 결과 |
|------|------|
| Claude 4.7 이상 (`opus-4.8`, `opus-4.7`) | 400 "`temperature` is deprecated for this model." |
| `gemini-3.5-flash-lite` | 값은 무시하고 매 요청 `UserWarning("uses fixed sampling defaults")` |
| `opus-4.6`, `haiku-4.5`, 그 밖의 Gemini | 정상 |

- 코드: `ai-server/app/llm/factory.py`의 `_NO_SAMPLING`. Claude 쪽은 "4.7 이상과 5 계열은 샘플링 미지원"이라는 규칙으로 판단한다.

## 3. 모델별 추론(thinking) 동작

- 측정: 2026-09-15, 두 제공사 키로 모델마다 직접 호출하고 응답 usage의 추론 토큰을 확인. 짧은 프롬프트 기준.
- 판정 기준: 파라미터를 받아주는지가 아니라 **추론 토큰이 실제로 나오는지**.

| 모델 | 기본 동작 | 끄는 법 / 조절 | 결과 |
|------|-----------|----------------|------|
| `claude-opus-4.8` | 추론 없음 | `effort`가 먹는다 (추론 61~65) | 모든 레벨 선택 가능 |
| `claude-opus-4.7` / `4.6` | 추론 없음 | `effort`를 200으로 받지만 추론은 늘 0 (3회 반복) | 노브를 열지 않음 |
| `claude-haiku-4.5` | 추론 없음 | `effort`를 보내면 400 "does not support the effort parameter" | 노브를 열지 않음 |
| `gemini-3.1-pro-preview` | 추론함 (57) | `thinking_budget=0`도 400, 끌 수 없음 | OFF 선택 불가 (백엔드가 LOW로 보정) |
| `gemini-3.5-flash` | 추론함 (59) | `thinking_budget=0`이면 0 | 모든 레벨 선택 가능 |
| `gemini-3.5-flash-lite` | 추론 없음 (0) | `budget=0`은 400이라 필드를 빼서 끈다. `low`는 200이지만 추론 0, `medium`부터 붙는다 | LOW 선택 불가 (OFF와 구별되지 않음) |

- 코드: 선택지는 `backend/.../aiClient/ModelCatalog.java`의 `ENTRIES`, 제공사 인자 변환은 `ai-server/app/llm/factory.py`.
- 모델 목록 자체는 2026-09-09에 두 제공사 키로 호출해 확인한 것만 올렸다. `openai/*`는 키가 없고, `google/gemini-2.5-pro`는 신규 사용자에게 막혀 404였다.

## 4. 요약 모델 선택

- 측정: 2026-09-09.
- 결과: Claude는 추론을 명시하지 않으면 추론하지 않는다. 그중 `haiku-4.5`가 가장 싸다.
- 의미: 요약은 추론 토큰이 `max_tokens`(2000)에서 나가므로, 추론하는 모델을 쓰면 요약문이 빈 채로 돌아올 수 있다.
- 코드: `ai-server/app/main.py`의 `SUMMARY_` 프로필.

## 5. 요약 형식 붕괴

- 측정: 2026-09-14, `claude-haiku-4.5`, 같은 대화 구간을 반복 요약.
- 결과: 고정 형식 명세를 프롬프트에만 두면 모델이 형식을 버리고 줄글 한 단락으로 답하는 일이 생긴다.

| [앞선 기록] 건수 | 프롬프트에만 명세 | 필드 설명에도 명세 |
|-----------------:|------------------:|-------------------:|
| 1건 | **6/12 붕괴** | 0/24 |
| 2건 | 0/9 | |
| 0건 | 0/4 | |

- 1건인 경우가 가장 위험한데, 방마다 꼭 한 번은 이 경우를 지난다(`SummaryPlan` 참고).
  구조화 출력에서는 필드 설명이 프롬프트보다 강하게 먹어서, 같은 명세를 필드 설명에 얹는 것만으로 해결됐다.
- 코드: `ai-server/app/memory/summarizer.py`의 `_FORMAT`, `_SUMMARY_FIELD`. 테스트 `ai-server/tests/test_summarizer.py`가 고정한다.

## 6. 이미지 모더레이션 기준점

- 측정: 2026-09-29, OpenAI `omni-moderation`. 샘플은 safe 10장, borderline 13장, unsafe 16장. 샘플 파일은 저장소에 올리지 않는다.
- `sexual` 점수:
  - safe는 전부 0.04 이하 (교복 일러스트 0.0008).
  - borderline은 대부분 0.02 이하. 명화 누드(비너스 0.24, 다비드 0.20), 비키니 아머 코스프레 0.78.
  - unsafe는 16장 전부 0.97 이상.
  - 기준 0.65로 safe 0/10, borderline 1/13(비키니 아머), unsafe 16/16 차단.
  - 0.25~0.78 구간에는 샘플이 없다. 이 사이 이미지가 쌓이면 다시 잰다.
- `violence` / `violence/graphic`:
  - 전투·검도·펜싱 사진은 graphic 0.05 이하지만 `violence`는 최대 0.77, `flagged`는 10장 중 8장.
    그래서 `violence`로는 전투 장면과 고어를 가를 수 없다.
  - 명화 고어(유디트, 사투르누스)는 graphic 0.36 이하, 게임·카툰·아이콘은 0.11 이하.
  - 실사풍 사진은 0.85~1.0 (좀비 분장·수술 사진·코스튬 0.85, 사실적인 가짜 열상 0.999).
  - 기준 0.9면 분장·수술 사진은 통과하고 극단적인 실사 상처만 막는다. 0.8이면 분장 사진까지 막힌다.
  - 애니·웹툰풍 고어 일러스트 샘플은 없다. 오탐이 나오면 그 유형으로 다시 잰다.
- `flagged`는 쓰지 않는다. violence 기준이 낮아 창 든 기사 판화(0.36)나 옛 의상 판화(0.20)까지 걸린다.
- 이미지 입력에서는 `sexual/minors`가 판정되지 않는다(텍스트 전용).
- 코드: `backend/.../upload/moderation/ModerationPolicy.java`.
