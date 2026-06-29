import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import CharacterForm from '../components/CharacterForm.jsx'
import LorebookPanel from '../components/LorebookPanel.jsx'

// 캐릭터 수정 페이지. 상단 탭으로 '기본정보'와 '로어북'을 오간다.
// (메인/공개 화면에서는 진입할 수 없고, 내 캐릭터 → ⋮ → 수정으로만 들어온다.)
export default function CharacterEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState('basic')
  const [character, setCharacter] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true
    api
      .getCharacter(id)
      .then((c) => {
        if (alive) setCharacter(c)
      })
      .catch((err) => {
        if (alive) setError(err.message || '캐릭터를 불러오지 못했습니다.')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [id])

  if (loading) return <p className="muted">불러오는 중…</p>
  if (error) return <p className="error">{error}</p>
  if (!character) return null

  return (
    <div>
      <div className="page-head">
        <div>
          <Link to="/my-characters" className="back-link">← 내 캐릭터</Link>
          <h1>{character.characterName}</h1>
        </div>
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
          onClick={() => setTab('lore')}
        >
          로어북
        </button>
      </div>

      {tab === 'basic' ? (
        <CharacterForm
          initial={{
            name: character.characterName,
            description: character.description ?? '',
            persona: character.persona ?? '',
          }}
          submitLabel="저장"
          savingLabel="저장 중…"
          cancelTo="/my-characters"
          onSubmit={async (form) => {
            await api.updateCharacter(id, form)
            navigate('/my-characters')
          }}
        />
      ) : (
        <LorebookPanel characterId={id} />
      )}
    </div>
  )
}
