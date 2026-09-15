// 대화에 쓸 추론(thinking) 깊이를 고르는 드롭다운.
// - 어떤 값을 고를 수 있는지는 모델마다 다르고, 그 서술(thinking.selectable)은 GET /models가 준다.
//   ModelPicker와 같은 규칙으로 프론트에 사본을 두지 않는다 — 여기 하드코딩된 건 라벨뿐이다.
// - 못 고르는 값도 목록에는 그대로 두고 비활성으로만 표시한다. 숨기면 "왜 이 모델엔 없지?"가
//   되지만, 회색으로 보이면 "이 모델은 안 되는구나"가 그 자리에서 읽힌다.
// - value(null=아직 안 고름) / onChange(레벨 문자열) 로 부모가 제어하는 controlled 컴포넌트.
const LABELS = {
  off: '끔',
  low: '낮게',
  medium: '보통',
  high: '깊게',
}

export default function ThinkingPicker({ levels, thinking, value, onChange, disabled = false, id }) {
  const all = levels ?? []
  const selectable = thinking?.selectable ?? []
  // 고른 값이 이 모델에 없으면 첫 선택지가 실제로 나가는 값이다(서버의 보정과 같은 규칙).
  // 한 번도 안 고른 방(null)도 이 규칙으로 덮인다.
  const selected = selectable.includes(value) ? value : selectable[0]

  return (
    <select
      id={id}
      className="model-select"
      value={selected ?? ''}
      onChange={(e) => onChange?.(e.target.value)}
      // ⚠️ 고를 게 하나뿐이라고 셀렉트째 잠그지 말 것 — 잠긴 셀렉트는 열리지가 않아서
      // 왜 못 고르는지(=다른 값들이 회색인 것)를 보여줄 방법이 사라진다. 목록을 아직 못
      // 받았을 때만 잠근다.
      disabled={disabled || all.length === 0}
      aria-label="추론 깊이"
    >
      {all.length === 0 && <option value="">불러오는 중…</option>}
      {all.map((level) => (
        <option key={level} value={level} disabled={!selectable.includes(level)}>
          {LABELS[level] ?? level}
        </option>
      ))}
    </select>
  )
}
