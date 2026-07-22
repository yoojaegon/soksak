import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'
import { useConfirm } from '../confirm.jsx'

// priority(@Min 1)·alwaysOn 기본값을 한 곳에 둔다.
const EMPTY_FORM = { title: '', keys: '', content: '', alwaysOn: false, priority: '1' }

// 한 캐릭터의 로어북 CRUD 패널.
// - 편집 모드(characterId): 변경이 즉시 API로 저장된다. (캐릭터 수정 페이지의 '로어북' 탭)
// - 초안 모드(draft): 아직 캐릭터가 없으므로 부모가 가진 lores 리스트만 갱신한다.
//   부모(캐릭터 만들기)가 '만들기' 시 캐릭터를 만든 뒤 이 초안들을 한꺼번에 저장한다.
export default function LorebookPanel({ characterId, draft = false, lores: loresProp, onChange }) {
  const confirm = useConfirm()
  // 편집 모드는 내부 상태로, 초안 모드는 부모가 넘긴 lores를 그대로 쓴다.
  const [internalLores, setInternalLores] = useState([])
  const lores = draft ? (loresProp ?? []) : internalLores
  // 초안 로어의 임시 id (음수로 부여해 실제 id와 겹치지 않게)
  const tempId = useRef(-1)

  const [loading, setLoading] = useState(!draft)
  const [error, setError] = useState('')
  // null = 폼 닫힘, 'new' = 새로 만들기, 숫자 id = 그 로어 수정 중
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  // 필수 입력 누락 시 각 입력창 아래 빨간 에러
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  // 토글/삭제 진행 중인 로어 id
  const [togglingId, setTogglingId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)

  // 리스트 갱신을 한 곳으로: 초안이면 부모에게, 아니면 내부 상태에.
  const writeLores = (fn) => {
    if (draft) onChange?.(fn(loresProp ?? []))
    else setInternalLores(fn)
  }

  useEffect(() => {
    if (draft) return // 초안 모드는 불러올 게 없다(빈 목록에서 시작)
    let alive = true
    api
      .getLores(characterId)
      .then((list) => {
        if (alive) setInternalLores(list ?? [])
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
  }, [characterId, draft])

  const onFormChange = (e) => {
    const { name, type, checked, value } = e.target
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
    if (type !== 'checkbox' && value.trim()) {
      setFieldErrors((prev) => ({ ...prev, [name]: undefined }))
    }
  }

  const openNew = () => {
    setEditing('new')
    setForm(EMPTY_FORM)
    setError('')
    setFieldErrors({})
  }

  const openEdit = (lore) => {
    setEditing(lore.id)
    setForm({
      title: lore.title,
      keys: lore.keys ?? '',
      content: lore.content,
      alwaysOn: lore.alwaysOn,
      priority: String(lore.priority),
    })
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
    // 필수 입력 검증
    const errs = {}
    if (!form.title.trim()) errs.title = '제목을 입력해주세요'
    if (!form.content.trim()) errs.content = '내용을 입력해주세요'
    // noValidate라 네이티브 min/step 검증이 없다. 서버 int(@Min 1)에 맞춰 정수인지도 여기서 본다.
    if (!Number.isInteger(Number(form.priority))) errs.priority = '우선순위는 정수로 입력해주세요'
    else if (Number(form.priority) < 1) errs.priority = '우선순위는 1 이상이어야 합니다'
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setFieldErrors({})
    setSaving(true)
    try {
      const body = {
        title: form.title.trim(),
        keys: form.keys.trim(),
        content: form.content.trim(),
        alwaysOn: form.alwaysOn,
        priority: Number(form.priority),
      }
      if (editing === 'new') {
        if (draft) {
          const created = { id: tempId.current--, ...body, enabled: true }
          writeLores((prev) => [...prev, created])
        } else {
          const created = await api.createLore(characterId, body)
          writeLores((prev) => [...prev, created])
        }
      } else if (draft) {
        writeLores((prev) => prev.map((l) => (l.id === editing ? { ...l, ...body } : l)))
      } else {
        const updated = await api.updateLore(characterId, editing, body)
        writeLores((prev) => prev.map((l) => (l.id === updated.id ? updated : l)))
      }
      closeForm()
    } catch (err) {
      setError(err.message || '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const toggleEnabled = async (lore) => {
    const next = !lore.enabled
    // 초안은 로컬에서만 뒤집는다.
    if (draft) {
      writeLores((prev) => prev.map((l) => (l.id === lore.id ? { ...l, enabled: next } : l)))
      return
    }
    setTogglingId(lore.id)
    setError('')
    // 낙관적 업데이트 — 실패하면 되돌린다.
    writeLores((prev) => prev.map((l) => (l.id === lore.id ? { ...l, enabled: next } : l)))
    try {
      const updated = await api.updateLoreEnabled(characterId, lore.id, next)
      writeLores((prev) => prev.map((l) => (l.id === updated.id ? updated : l)))
    } catch (err) {
      writeLores((prev) => prev.map((l) => (l.id === lore.id ? { ...l, enabled: lore.enabled } : l)))
      setError(err.message || '상태 변경에 실패했습니다.')
    } finally {
      setTogglingId(null)
    }
  }

  const remove = async (lore) => {
    const ok = await confirm({
      title: '이 로어를 삭제할까요?',
      message: `'${lore.title}'을(를) 로어북에서 지웁니다.`,
      confirmLabel: '삭제',
      danger: true,
    })
    if (!ok) return
    if (draft) {
      writeLores((prev) => prev.filter((l) => l.id !== lore.id))
      if (editing === lore.id) closeForm()
      return
    }
    setDeletingId(lore.id)
    setError('')
    try {
      await api.deleteLore(characterId, lore.id)
      writeLores((prev) => prev.filter((l) => l.id !== lore.id))
      if (editing === lore.id) closeForm()
    } catch (err) {
      setError(err.message || '삭제에 실패했습니다.')
    } finally {
      setDeletingId(null)
    }
  }

  if (loading) return <p className="muted">불러오는 중…</p>

  // priority 큰 순(우선) → 작은 순으로 보여준다.
  const sorted = [...lores].sort((a, b) => b.priority - a.priority)

  return (
    <div>
      <div className="lore-panel-head">
        <p className="muted">
          캐릭터가 알아야 할 설정·세계관을 적어 둬요. 키워드가 대화에 등장하면 자동으로 끼워 넣고, ‘항상 적용’은 늘 주입합니다.
          {draft && ' 여기서 추가한 로어는 ‘만들기’를 누르면 캐릭터와 함께 저장됩니다.'}
        </p>
        {editing === null && (
          <button className="btn-link" onClick={openNew}>+ 로어 추가</button>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      {editing !== null && (
        <div className="form-card lore-form">
          <h2>{editing === 'new' ? '로어 추가' : '로어 수정'}</h2>
          {/* noValidate: 브라우저 기본 검증 말풍선 대신 앱 스타일 field-error로 보여준다.
              (네이티브 min="1"이 submit을 먼저 막아 아래 priority 검증이 실행되지 않았다) */}
          <form onSubmit={onSubmit} noValidate>
            <label>
              <span className="field-caption">제목 <span className="req">*</span></span>
              <input name="title" value={form.title} onChange={onFormChange} placeholder="예: 주인공의 과거" />
              {fieldErrors.title && <p className="field-error">! {fieldErrors.title}</p>}
            </label>
            <label>
              <span className="field-caption">키워드 (쉼표로 구분, 대화에 등장하면 주입)</span>
              <input name="keys" value={form.keys} onChange={onFormChange} placeholder="예: 고향, 어린 시절, 형" />
            </label>
            <label>
              <span className="field-caption">내용 <span className="req">*</span></span>
              <textarea
                name="content"
                value={form.content}
                onChange={onFormChange}
                rows={5}
                placeholder="캐릭터가 기억해야 할 설정을 적어 주세요."
              />
              {fieldErrors.content && <p className="field-error">! {fieldErrors.content}</p>}
            </label>
            <div className="form-row">
              <label className="lore-priority">
                <span className="field-caption">우선순위 <span className="req">*</span> (클수록 먼저)</span>
                <input name="priority" type="number" min="1" value={form.priority} onChange={onFormChange} />
                {fieldErrors.priority && <p className="field-error">! {fieldErrors.priority}</p>}
              </label>
              <label className="lore-alwayson">
                <input type="checkbox" name="alwaysOn" checked={form.alwaysOn} onChange={onFormChange} />
                항상 적용 (키워드 없이 늘 주입)
              </label>
            </div>
            <div className="form-actions">
              <button type="button" className="link-btn" onClick={closeForm}>취소</button>
              <button type="submit" disabled={saving}>
                {saving ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        </div>
      )}

      {sorted.length === 0 ? (
        <p className="muted">아직 로어가 없어요. 새로 추가해 보세요.</p>
      ) : (
        <div className="lore-list">
          {sorted.map((lore) => (
            <div className={`lore-card${lore.enabled ? '' : ' disabled'}`} key={lore.id}>
              <div className="lore-card-head">
                <h3>{lore.title}</h3>
                <div className="lore-tags">
                  {lore.alwaysOn && <span className="badge">항상</span>}
                  <span className="badge ghost">P{lore.priority}</span>
                </div>
              </div>
              {lore.keys && <p className="lore-keys">🔑 {lore.keys}</p>}
              <p className="lore-content">{lore.content}</p>
              <div className="lore-actions">
                <button
                  className="link-btn"
                  onClick={() => toggleEnabled(lore)}
                  disabled={togglingId === lore.id}
                >
                  {lore.enabled ? '끄기' : '켜기'}
                </button>
                <button className="link-btn" onClick={() => openEdit(lore)}>수정</button>
                <button
                  className="link-btn danger"
                  onClick={() => remove(lore)}
                  disabled={deletingId === lore.id}
                >
                  {deletingId === lore.id ? '삭제 중…' : '삭제'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
