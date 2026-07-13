import { useState } from 'react'
import { Link } from 'react-router-dom'
import CharacterFields from './CharacterFields.jsx'
import { toggleTag } from '../genres.js'

// 캐릭터 기본정보 폼 (캐릭터 수정 페이지에서 사용).
// - 폼 상태·저장중/에러 표시는 여기서 관리한다.
// - onSubmit(form)은 부모가 넘긴다(api 호출 + 이동). 실패하면 throw해 에러를 표시한다.
export default function CharacterForm({ heading, initial, submitLabel, savingLabel, onSubmit, cancelTo }) {
  const [form, setForm] = useState(initial)
  // 필수 입력 누락 시 각 입력창 아래 빨간 에러 (생성 페이지와 동일한 방식)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    if (value.trim()) setFieldErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  // 장르 칩 토글: 이미 있으면 빼고 없으면 넣는다. 토글하면 장르 필수 에러는 지운다.
  const handleToggleTag = (value) => {
    setForm((f) => ({ ...f, tags: toggleTag(f.tags ?? [], value) }))
    setFieldErrors((prev) => ({ ...prev, tags: undefined }))
  }

  // 필수 기본정보(이름·페르소나·첫 인사말·장르 최소 1개) 검증
  const validate = () => {
    const errs = {}
    if (!form.name.trim()) errs.name = '캐릭터명을 입력해주세요'
    if (!form.persona.trim()) errs.persona = '페르소나를 입력해주세요'
    if (!form.greeting.trim()) errs.greeting = '첫 인사말을 입력해주세요'
    if (!form.tags?.length) errs.tags = '장르를 하나 이상 선택해주세요'
    return errs
  }

  const submit = async (e) => {
    e.preventDefault()
    const errs = validate()
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setError('')
    setSaving(true)
    try {
      await onSubmit(form)
      // 성공하면 보통 부모가 화면을 떠나므로 saving은 그대로 둔다.
    } catch (err) {
      setError(err.message || '저장에 실패했습니다.')
      setSaving(false)
    }
  }

  return (
    <div className="form-card">
      {heading && <h1>{heading}</h1>}
      <form onSubmit={submit}>
        <CharacterFields form={form} onChange={handleChange} onToggleTag={handleToggleTag} errors={fieldErrors} />
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <Link to={cancelTo} className="link-btn">취소</Link>
          <button type="submit" disabled={saving}>
            {saving ? savingLabel : submitLabel}
          </button>
        </div>
      </form>
    </div>
  )
}
