// 대화에 쓰는 LLM 모델 프론트 유틸.
// 카탈로그(선택지·라벨·기본값)의 단일 출처는 백엔드 GET /models다.
// 프론트는 폴백 카탈로그를 갖지 않는다 — 목록을 못 받으면 픽커를 비활성화하고
// 저장된 슬러그를 그대로 보여준다(백엔드와 어긋난 복사본이 생기지 않게).

// 슬러그 → 라벨. 목록에 없으면 슬러그를 그대로 돌려준다(백엔드가 목록을 늘렸을 때 안전).
export const modelLabel = (models, id) =>
  models.find((m) => m.id === id)?.label ?? id

// 슬러그 → 추론 능력 서술(지원하나 / 끌 수 있나 / 어떤 단계가 있나).
// 목록을 아직 못 받았으면 null — 픽커는 그걸 "고를 수 없음"으로 그린다.
export const thinkingOf = (models, id) =>
  (models ?? []).find((m) => m.id === id)?.thinking ?? null
