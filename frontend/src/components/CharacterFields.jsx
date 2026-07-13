import { GENRES } from '../genres.js'

// 캐릭터 기본정보 입력 필드들 (이름·소개·장르·페르소나·첫 인사말).
// 폼 상태는 부모가 들고, 여기서는 보여주기만 하는 controlled 컴포넌트.
// onToggleTag(value): 장르 칩 토글(부모가 form.tags 배열을 갱신).
export default function CharacterFields({ form, onChange, onToggleTag, errors = {} }) {
  const tags = form.tags ?? []
  return (
    <>
      <label>
        <span className="field-caption">이름 <span className="req">*</span></span>
        <input name="name" value={form.name} onChange={onChange} placeholder="예: 하루" />
        {errors.name && <p className="field-error">! {errors.name}</p>}
      </label>
      <label>
        <span className="field-caption">소개 (선택)</span>
        <input
          name="description"
          value={form.description}
          onChange={onChange}
          placeholder="목록에 보일 한 줄 소개"
        />
      </label>
      <div className="field-block">
        <span className="field-caption">장르 <span className="req">*</span> (하나 이상)</span>
        <div className="chip-picker">
          {GENRES.map((g) => (
            <button
              key={g.value}
              type="button"
              className={`chip${tags.includes(g.value) ? ' active' : ''}`}
              aria-pressed={tags.includes(g.value)}
              onClick={() => onToggleTag?.(g.value)}
            >
              {g.label}
            </button>
          ))}
        </div>
        {errors.tags && <p className="field-error">! {errors.tags}</p>}
      </div>
      <label>
        <span className="field-caption">페르소나 <span className="req">*</span> (성격·말투)</span>
        <textarea
          name="persona"
          value={form.persona}
          onChange={onChange}
          rows={5}
          placeholder="예: 너는 항상 밝고 친근하게 반말로 대답하는 친구야."
        />
        {errors.persona && <p className="field-error">! {errors.persona}</p>}
      </label>
      <label>
        <span className="field-caption">첫 인사말 <span className="req">*</span> (새 대화를 열 때 캐릭터가 먼저 건네는 말)</span>
        <textarea
          name="greeting"
          value={form.greeting}
          onChange={onChange}
          rows={3}
          placeholder="예: 안녕! 드디어 만났네. 오늘은 무슨 얘기를 해볼까?"
        />
        {errors.greeting && <p className="field-error">! {errors.greeting}</p>}
      </label>
    </>
  )
}
