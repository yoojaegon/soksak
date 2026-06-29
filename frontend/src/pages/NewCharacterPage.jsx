import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import CharacterFields from '../components/CharacterFields.jsx'
import LorebookPanel from '../components/LorebookPanel.jsx'

// 캐릭터 생성 페이지. 수정 페이지처럼 상단 탭(기본정보 / 로어북)을 두고,
// '만들기' 버튼은 탭 아래 공용 하단 바에 둬서 어느 탭에서든 누를 수 있게 한다.
// 로어북은 아직 캐릭터가 없으므로 초안으로 모아 두었다가, 만들기 시 캐릭터를 만든 직후 함께 저장한다.
export default function NewCharacterPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState('basic')
  const [form, setForm] = useState({ name: '', description: '', persona: '' })
  // 필수 입력 누락 시 각 입력창 아래 빨간 에러
  const [fieldErrors, setFieldErrors] = useState({})
  // 로어북 탭에서 모은 초안 로어들 (탭을 오가도 유지되도록 여기서 보관)
  const [draftLores, setDraftLores] = useState([])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    if (value.trim()) setFieldErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  // 필수 기본정보(이름·페르소나) 검증
  const validate = () => {
    const errs = {}
    if (!form.name.trim()) errs.name = '캐릭터명을 입력해주세요'
    if (!form.persona.trim()) errs.persona = '페르소나를 입력해주세요'
    return errs
  }

  // 로어북 탭으로 넘어가려면 필수 기본정보가 채워져 있어야 한다.
  const goToLore = () => {
    const errs = validate()
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setTab('lore')
  }

  const create = async () => {
    const errs = validate()
    if (Object.keys(errs).length > 0) {
      // 필수가 비었으면 기본정보 탭으로 돌아가 에러를 보여준다.
      setFieldErrors(errs)
      setTab('basic')
      return
    }
    setError('')
    setSaving(true)
    try {
      const created = await api.createCharacter(form)
      // 캐릭터 생성 성공 후 초안 로어들을 새 캐릭터에 저장한다.
      try {
        for (const l of draftLores) {
          await api.createLore(created.id, {
            title: l.title,
            keys: l.keys,
            content: l.content,
            alwaysOn: l.alwaysOn,
            priority: l.priority,
          })
        }
      } catch {
        alert('캐릭터는 만들어졌지만 일부 로어 저장에 실패했어요. 캐릭터 수정에서 다시 추가해 주세요.')
      }
      navigate('/my-characters')
    } catch (err) {
      setError(err.message || '저장에 실패했습니다.')
      setSaving(false)
      setTab('basic')
    }
  }

  return (
    <div>
      <div className="page-head">
        <h1>캐릭터 만들기</h1>
      </div>

      <div className="tabs">
        <button
          className={`tab${tab === 'basic' ? ' active' : ''}`}
          onClick={() => setTab('basic')}
        >
          캐릭터 기본정보
        </button>
        <button
          className={`tab${tab === 'lore' ? ' active' : ''}`}
          onClick={goToLore}
        >
          로어북{draftLores.length > 0 ? ` (${draftLores.length})` : ''}
        </button>
      </div>

      {tab === 'basic' ? (
        <div className="form-card create-card">
          <CharacterFields form={form} onChange={onChange} errors={fieldErrors} />
        </div>
      ) : (
        <LorebookPanel draft lores={draftLores} onChange={setDraftLores} />
      )}

      {error && <p className="error">{error}</p>}
      {/* 어느 탭에서든 만들 수 있는 공용 하단 바 */}
      <div className="form-actions create-actions">
        <Link to="/my-characters" className="link-btn">취소</Link>
        <button onClick={create} disabled={saving}>
          {saving ? '만드는 중…' : '만들기'}
        </button>
      </div>
    </div>
  )
}
