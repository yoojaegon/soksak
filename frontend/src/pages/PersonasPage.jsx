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
  // 필수 입력 누락 시 각 입력창 아래 빨간 에러
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  // 기본 지정 진행 중인 페르소나 id
  const [defaultingId, setDefaultingId] = useState(null)
  // 삭제 진행 중인 페르소나 id
  const [deletingId, setDeletingId] = useState(null)
  // ⋮ 메뉴가 열려 있는 카드 id (null = 모두 닫힘)
  const [menuId, setMenuId] = useState(null)

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

  // 메뉴가 열려 있으면 바깥을 클릭했을 때 닫는다.
  useEffect(() => {
    if (menuId === null) return
    const close = () => setMenuId(null)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [menuId])

  const onChange = (e) => {
    const { name, value } = e.target
    setForm({ ...form, [name]: value })
    if (value.trim()) setFieldErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  const openNew = () => {
    setEditing('new')
    setForm(EMPTY_FORM)
    setError('')
    setFieldErrors({})
  }

  const openEdit = (p) => {
    setEditing(p.id)
    setForm({ name: p.name, gender: p.gender, age: String(p.age), persona: p.persona })
    setError('')
    setFieldErrors({})
  }

  const closeForm = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFieldErrors({})
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    // 필수 입력 검증 (성별은 항상 기본값이 선택돼 있어 제외)
    const errs = {}
    if (!form.name.trim()) errs.name = '이름을 입력해주세요'
    if (form.age === '') errs.age = '나이를 입력해주세요'
    if (!form.persona.trim()) errs.persona = '페르소나를 입력해주세요'
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setFieldErrors({})
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
    setMenuId(null)
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
    setMenuId(null)
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
              <span className="field-caption">이름 <span className="req">*</span></span>
              <input name="name" value={form.name} onChange={onChange} placeholder="예: 지민" />
              {fieldErrors.name && <p className="field-error">! {fieldErrors.name}</p>}
            </label>
            <div className="form-row">
              <label>
                <span className="field-caption">성별 <span className="req">*</span></span>
                <select name="gender" value={form.gender} onChange={onChange}>
                  {GENDERS.map((g) => (
                    <option key={g.value} value={g.value}>{g.label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span className="field-caption">나이 <span className="req">*</span></span>
                <input name="age" type="number" min="0" value={form.age} onChange={onChange} placeholder="예: 25" />
                {fieldErrors.age && <p className="field-error">! {fieldErrors.age}</p>}
              </label>
            </div>
            <label>
              <span className="field-caption">페르소나 <span className="req">*</span> (어떤 사람인지)</span>
              <textarea
                name="persona"
                value={form.persona}
                onChange={onChange}
                rows={4}
                placeholder="예: 나는 호기심 많고 장난기 있는 대학생이다."
              />
              {fieldErrors.persona && <p className="field-error">! {fieldErrors.persona}</p>}
            </label>
            <div className="form-actions">
              <button type="button" className="link-btn" onClick={closeForm}>취소</button>
              <button type="submit" disabled={saving}>
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
              {/* ⋮ 기본 지정·수정·삭제 메뉴 */}
              <div className="card-menu">
                <button
                  type="button"
                  className="card-menu-btn"
                  aria-label="더보기"
                  onClick={(e) => {
                    e.stopPropagation()
                    setMenuId((id) => (id === p.id ? null : p.id))
                  }}
                >
                  ⋮
                </button>
                {menuId === p.id && (
                  <div className="card-menu-pop" onClick={(e) => e.stopPropagation()}>
                    {!p.isDefault && (
                      <button onClick={() => makeDefault(p.id)} disabled={defaultingId === p.id}>
                        {defaultingId === p.id ? '설정 중…' : '기본으로 설정'}
                      </button>
                    )}
                    <button onClick={() => { setMenuId(null); openEdit(p) }}>페르소나 수정</button>
                    {/* 마지막 한 개는 삭제 불가 → 2개 이상일 때만 노출 */}
                    {personas.length > 1 && (
                      <button
                        className="danger"
                        onClick={() => remove(p)}
                        disabled={deletingId === p.id}
                      >
                        {deletingId === p.id ? '삭제 중…' : '페르소나 삭제'}
                      </button>
                    )}
                  </div>
                )}
              </div>

              <div className="persona-card-head">
                <h3>{p.name}</h3>
                {p.isDefault && <span className="badge">기본</span>}
              </div>
              <p className="muted">{genderLabel(p.gender)} · {p.age}세</p>
              <p className="muted persona-text">{p.persona}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
