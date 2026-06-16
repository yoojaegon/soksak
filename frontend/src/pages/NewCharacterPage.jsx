import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'

export default function NewCharacterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', description: '', persona: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.createCharacter(form)
      // 생성 성공 → 목록으로
      navigate('/')
    } catch (err) {
      setError(err.message || '캐릭터 생성에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="form-card">
      <h1>캐릭터 만들기</h1>
      <form onSubmit={onSubmit}>
        <label>
          이름
          <input name="name" value={form.name} onChange={onChange} placeholder="예: 하루" />
        </label>
        <label>
          소개 (선택)
          <input
            name="description"
            value={form.description}
            onChange={onChange}
            placeholder="목록에 보일 한 줄 소개"
          />
        </label>
        <label>
          페르소나 (성격·말투)
          <textarea
            name="persona"
            value={form.persona}
            onChange={onChange}
            rows={5}
            placeholder="예: 너는 항상 밝고 친근하게 반말로 대답하는 친구야."
          />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <Link to="/" className="link-btn">취소</Link>
          <button type="submit" disabled={loading || !form.name.trim() || !form.persona.trim()}>
            {loading ? '만드는 중…' : '만들기'}
          </button>
        </div>
      </form>
    </div>
  )
}
