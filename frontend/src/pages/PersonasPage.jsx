import { useEffect, useState } from 'react'
import { api } from '../api.js'

// 성별 코드 ↔ 한글 라벨 (백엔드 Gender enum과 1:1)
const GENDERS = [
  { value: 'MALE', label: '남성' },
  { value: 'FEMALE', label: '여성' },
  { value: 'OTHER', label: '기타' },
]
const genderLabel = (g) => GENDERS.find((x) => x.value === g)?.label ?? g

const EMPTY_FORM = { name: '', gender: 'MALE', age: '', persona: '' }

export default function PersonasPage() {
  const [personas, setPersonas] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // null = 폼 닫힘, 'new' = 새로 만들기, 숫자 id = 그 페르소나 수정 중
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  // 기본 지정 진행 중인 페르소나 id
  const [defaultingId, setDefaultingId] = useState(null)
  // 삭제 진행 중인 페르소나 id
  const [deletingId, setDeletingId] = useState(null)

  useEffect(() => {
    let alive = true
    api
      .getUserPersonas()
      .then((data) => {
        if (alive) setPersonas(data ?? [])
      })
      .catch((err) => {
        if (alive) setError(err.message)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [])

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const openNew = () => {
    setEditing('new')
    setForm(EMPTY_FORM)
    setError('')
  }

  const openEdit = (p) => {
    setEditing(p.id)
    setForm({ name: p.name, gender: p.gender, age: String(p.age), persona: p.persona })
    setError('')
  }

  const closeForm = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body = {
        name: form.name.trim(),
        gender: form.gender,
        age: Number(form.age),
        persona: form.persona.trim(),
      }
      if (editing === 'new') {
        const created = await api.createUserPersona(body)
        setPersonas((prev) => [...prev, created])
      } else {
        const updated = await api.updateUserPersona(editing, body)
        setPersonas((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
      }
      closeForm()
    } catch (err) {
      setError(err.message || '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const makeDefault = async (id) => {
    setDefaultingId(id)
    setError('')
    try {
      await api.setDefaultUserPersona(id)
      // 응답으로 하나만 받지만, 화면 전체의 isDefault를 다시 계산한다.
      setPersonas((prev) => prev.map((p) => ({ ...p, isDefault: p.id === id })))
    } catch (err) {
      setError(err.message || '기본 설정에 실패했습니다.')
    } finally {
      setDefaultingId(null)
    }
  }

  const remove = async (p) => {
    if (!window.confirm(`'${p.name}' 페르소나를 삭제할까요?`)) return
    setDeletingId(p.id)
    setError('')
    try {
      await api.deleteUserPersona(p.id)
      // 기본을 지우면 서버가 다른 걸 기본으로 올리므로, 목록을 다시 불러와 반영한다.
      const data = await api.getUserPersonas()
      setPersonas(data ?? [])
      // 수정 중이던 게 삭제 대상이면 폼을 닫는다.
      if (editing === p.id) closeForm()
    } catch (err) {
      setError(err.message || '삭제에 실패했습니다.')
    } finally {
      setDeletingId(null)
    }
  }

  if (loading) return <p className="muted">불러오는 중…</p>

  const canSave = form.name.trim() && form.persona.trim() && form.age !== ''

  return (
    <div>
      <div className="page-head">
        <h1>내 페르소나</h1>
        {editing === null && (
          <button className="btn-link" onClick={openNew}>+ 페르소나 만들기</button>
        )}
      </div>
      <p className="muted">대화할 때 내가 어떤 사람으로 등장할지 정해요. 기본으로 지정한 페르소나가 대화에 쓰입니다.</p>
      {error && <p className="error">{error}</p>}

      {editing !== null && (
        <div className="form-card persona-form">
          <h2>{editing === 'new' ? '페르소나 만들기' : '페르소나 수정'}</h2>
          <form onSubmit={onSubmit}>
            <label>
              이름
              <input name="name" value={form.name} onChange={onChange} placeholder="예: 지민" />
            </label>
            <div className="form-row">
              <label>
                성별
                <select name="gender" value={form.gender} onChange={onChange}>
                  {GENDERS.map((g) => (
                    <option key={g.value} value={g.value}>{g.label}</option>
                  ))}
                </select>
              </label>
              <label>
                나이
                <input name="age" type="number" min="0" value={form.age} onChange={onChange} placeholder="예: 25" />
              </label>
            </div>
            <label>
              페르소나 (어떤 사람인지)
              <textarea
                name="persona"
                value={form.persona}
                onChange={onChange}
                rows={4}
                placeholder="예: 나는 호기심 많고 장난기 있는 대학생이다."
              />
            </label>
            <div className="form-actions">
              <button type="button" className="link-btn" onClick={closeForm}>취소</button>
              <button type="submit" disabled={saving || !canSave}>
                {saving ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        </div>
      )}

      {personas.length === 0 ? (
        <p className="muted">아직 페르소나가 없어요. 새로 만들어 보세요.</p>
      ) : (
        <div className="card-grid">
          {personas.map((p) => (
            <div className="char-card" key={p.id}>
              <div className="persona-card-head">
                <h3>{p.name}</h3>
                {p.isDefault && <span className="badge">기본</span>}
              </div>
              <p className="muted">{genderLabel(p.gender)} · {p.age}세</p>
              <p className="muted persona-text">{p.persona}</p>
              <div className="persona-actions">
                {!p.isDefault && (
                  <button
                    className="link-btn"
                    onClick={() => makeDefault(p.id)}
                    disabled={defaultingId === p.id}
                  >
                    {defaultingId === p.id ? '설정 중…' : '기본으로'}
                  </button>
                )}
                <button className="link-btn" onClick={() => openEdit(p)}>수정</button>
                {/* 마지막 한 개는 삭제 불가 → 2개 이상일 때만 노출 */}
                {personas.length > 1 && (
                  <button
                    className="link-btn danger"
                    onClick={() => remove(p)}
                    disabled={deletingId === p.id}
                  >
                    {deletingId === p.id ? '삭제 중…' : '삭제'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
