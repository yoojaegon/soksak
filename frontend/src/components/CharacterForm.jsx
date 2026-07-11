import { useState } from 'react'
import { Link } from 'react-router-dom'
import CharacterFields from './CharacterFields.jsx'
import { toggleTag } from '../genres.js'

// 캐릭터 기본정보 폼 (캐릭터 수정 페이지에서 사용).
// - 폼 상태·저장중/에러 표시는 여기서 관리한다.
// - onSubmit(form)은 부모가 넘긴다(api 호출 + 이동). 실패하면 throw해 에러를 표시한다.
export default function CharacterForm({ heading, initial, submitLabel, savingLabel, onSubmit, cancelTo }) {
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  // 장르 칩 토글: 이미 있으면 빼고 없으면 넣는다.
  const handleToggleTag = (value) =>
    setForm((f) => ({ ...f, tags: toggleTag(f.tags ?? [], value) }))

  const submit = async (e) => {
    e.preventDefault()
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
        <CharacterFields form={form} onChange={handleChange} onToggleTag={handleToggleTag} />
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <Link to={cancelTo} className="link-btn">취소</Link>
          <button type="submit" disabled={saving || !form.name.trim() || !form.persona.trim() || !form.greeting.trim()}>
            {saving ? savingLabel : submitLabel}
          </button>
        </div>
      </form>
    </div>
  )
}
