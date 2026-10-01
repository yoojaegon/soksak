import { useEffect, useRef, useState } from 'react'

// 신고 창. 캐릭터 신고(이미지·설정)와 채팅 응답 신고를 한 창으로 받는다.
// 입력칸이 있어 confirm.jsx 대신 네이티브 <dialog>로 띄운다(비밀번호 변경 창과 같은 방식).
//
//   kind="character" → 무엇이 문제인지(이미지/설정)부터 고른다
//   kind="message"   → 대상이 정해져 있어 사유부터. 대화 내용이 운영자에게 간다는 고지를 붙인다
//
// onSubmit(body)가 던지면 창 안에 에러를 띄우고, 성공하면 onDone()을 부른다.
// 결과는 "접수됐다"로만 알린다 — 숨겨졌는지는 서버도 알려주지 않는다.

const TARGETS = [
  { value: 'IMAGE', label: '이미지' },
  { value: 'CONCEPT', label: '캐릭터 설정·소개' },
]

// 값은 백엔드 ReportReason과 같아야 한다.
const REASONS = [
  { value: 'MINOR', label: '미성년자를 성적으로 묘사함' },
  { value: 'SEXUAL', label: '지나친 성적 묘사' },
  { value: 'VIOLENCE', label: '지나친 폭력·잔인함' },
  { value: 'HATE', label: '혐오·차별' },
  { value: 'OTHER', label: '기타' },
]

// 백엔드 Report*Request의 @Size(max)와 같은 값.
const DETAIL_MAX = 200

export default function ReportDialog({ kind, onSubmit, onClose, onDone, returnFocusRef }) {
  const dialogRef = useRef(null)
  const downOnBackdrop = useRef(false)
  const [target, setTarget] = useState(null)
  const [reason, setReason] = useState(null)
  const [detail, setDetail] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog.showModal()
    return () => {
      dialog.close()
      // 모달이 떠 있는 동안엔 바깥이 inert라 focus()가 먹지 않는다. 닫은 다음에 돌려준다.
      returnFocusRef?.current?.focus()
    }
  }, [returnFocusRef])

  const isCharacter = kind === 'character'
  const ready = reason && (!isCharacter || target)

  const submit = async (e) => {
    e.preventDefault()
    if (!ready || submitting) return
    setSubmitting(true)
    setError('')
    const body = { reason, detail: detail.trim() || null }
    if (isCharacter) body.target = target
    try {
      await onSubmit(body)
    } catch (err) {
      setSubmitting(false)
      return setError(err.message || '신고하지 못했습니다.')
    }
    onDone()
  }

  return (
    <dialog
      ref={dialogRef}
      className="pw-dialog report-dialog"
      aria-labelledby="report-title"
      // Esc·바깥 클릭 처리는 비밀번호 변경 창(ProfilePage)과 같다 — 이유는 그쪽 주석 참고.
      onCancel={(e) => {
        e.preventDefault()
        if (!submitting) onClose()
      }}
      onClose={(e) => {
        if (!e.currentTarget.open) onClose()
      }}
      onMouseDown={(e) => {
        downOnBackdrop.current = e.target === e.currentTarget
      }}
      onClick={(e) => {
        if (downOnBackdrop.current && e.target === e.currentTarget && !submitting) onClose()
      }}
    >
      <div className="confirm-bar" />
      <form className="pw-body" onSubmit={submit} noValidate>
        <div>
          <h2 className="confirm-title" id="report-title">
            {isCharacter ? '캐릭터 신고' : '응답 신고'}
          </h2>
          {!isCharacter && (
            <p className="confirm-msg">
              신고한 응답과 바로 앞의 내 메시지가 운영자에게 전달됩니다.
            </p>
          )}
        </div>

        {isCharacter && (
          <fieldset className="report-group">
            <legend className="field-caption">무엇이 문제인가요?</legend>
            {TARGETS.map((t) => (
              <label key={t.value} className="report-option">
                <input type="radio" name="target" value={t.value}
                  checked={target === t.value} onChange={() => setTarget(t.value)} />
                <span>{t.label}</span>
              </label>
            ))}
          </fieldset>
        )}

        <fieldset className="report-group">
          <legend className="field-caption">사유</legend>
          {REASONS.map((r) => (
            <label key={r.value} className="report-option">
              <input type="radio" name="reason" value={r.value}
                checked={reason === r.value} onChange={() => setReason(r.value)} />
              <span>{r.label}</span>
            </label>
          ))}
        </fieldset>

        <label>
          <span className="field-caption">자세한 내용 (선택)</span>
          <textarea
            className="report-detail"
            rows={3}
            maxLength={DETAIL_MAX}
            value={detail}
            onChange={(e) => setDetail(e.target.value)}
          />
          <p className="field-hint">{detail.length} / {DETAIL_MAX}</p>
        </label>

        {error && <p className="error">{error}</p>}
        <div className="confirm-actions pw-actions">
          <button type="button" className="ghost" onClick={onClose} disabled={submitting}>취소</button>
          <button type="submit" disabled={!ready || submitting}>{submitting ? '보내는 중…' : '신고하기'}</button>
        </div>
      </form>
      <div className="confirm-bar" />
    </dialog>
  )
}
