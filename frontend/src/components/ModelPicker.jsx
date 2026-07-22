// 대화에 사용할 LLM 모델을 고르는 드롭다운.
// - 목록·기본값은 부모(ChatPage)가 백엔드 GET /models에서 한 번 받아 props로 내려준다.
// - value(슬러그) / onChange(슬러그) 로 부모가 제어하는 controlled 컴포넌트.
// - models가 비어 있으면(null=로딩 중·조회 실패, []=백엔드가 빈 목록) 비활성화하고 현재 값만
//   보여준다 — 프론트에 폴백 카탈로그를 두지 않는다(백엔드와 어긋난 복사본 방지).
export default function ModelPicker({ models, defaultModel, value, onChange, disabled = false, id }) {
  // 방에 저장된 모델이 없으면(null) 실제로 쓰일 기본 모델을 그 자리에 보여준다.
  // 목록에 기본 모델이 이미 있으므로 "기본 모델" 같은 별도 항목은 두지 않는다(같은 모델이 두 번 보임).
  const selected = value ?? defaultModel
  const list = models ?? []
  // 저장된 슬러그가 목록에 없으면(미로드·백엔드가 옵션을 바꿈 등) 빈 select가 되지 않도록
  // 현재 값을 슬러그 라벨로 임시 추가한다.
  const options =
    selected && !list.some((m) => m.id === selected)
      ? [{ id: selected, label: selected }, ...list]
      : list

  return (
    <select
      id={id}
      className="model-select"
      value={selected ?? ''}
      onChange={(e) => onChange?.(e.target.value)}
      disabled={disabled || !models?.length}
      aria-label="대화 모델"
    >
      {options.length === 0 && <option value="">모델 불러오는 중…</option>}
      {options.map((m) => (
        <option key={m.id} value={m.id}>
          {m.label}
        </option>
      ))}
    </select>
  )
}
