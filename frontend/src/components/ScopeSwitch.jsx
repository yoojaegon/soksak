// 설정 값을 어디에 저장할지 고르는 두 칸짜리 스위치(이 대화만 / 기본값).
// - followsDefault=true면 "기본값"이 눌린 상태다. 이때 값을 바꾸면 사용자 기본값이 바뀌고,
//   기본값을 따르는 다른 대화에도 함께 적용된다.
// - onChange(toDefault)로 부모가 서버에 반영한다. 화면은 서버 응답으로만 바뀐다.
export default function ScopeSwitch({ followsDefault, onChange, disabled = false, label }) {
  return (
    <div className="scope-switch" role="group" aria-label={label}>
      <button
        type="button"
        aria-pressed={!followsDefault}
        className={!followsDefault ? 'on' : ''}
        onClick={() => onChange?.(false)}
        disabled={disabled}
      >
        이 대화만
      </button>
      <button
        type="button"
        aria-pressed={followsDefault}
        className={followsDefault ? 'on' : ''}
        onClick={() => onChange?.(true)}
        disabled={disabled}
      >
        기본값
      </button>
    </div>
  )
}
