import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'

// 성별 코드 ↔ 한글 라벨 (백엔드 Gender enum과 1:1)
const GENDERS = [
  { value: 'MALE', label: '남성' },
  { value: 'FEMALE', label: '여성' },
  { value: 'OTHER', label: '기타' },
]

export default function SignupPage() {
  const navigate = useNavigate()
  // age/gender는 가입 시 기본 페르소나 생성에 쓰여 백엔드에서 필수다.
  const [form, setForm] = useState({
    email: '', loginId: '', nickname: '', password: '', age: '', gender: 'MALE',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      // age는 숫자로 변환해 보낸다(백엔드 Integer).
      await api.signup({ ...form, age: Number(form.age) })
      // 가입 성공 → 로그인 페이지로
      navigate('/login')
    } catch (err) {
      setError(err.message || '회원가입에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-card">
      <h1>회원가입</h1>
      <form onSubmit={onSubmit}>
        <label>
          이메일
          <input name="email" type="email" value={form.email} onChange={onChange} />
        </label>
        <label>
          아이디 (영문·숫자 4~20자)
          <input name="loginId" value={form.loginId} onChange={onChange} autoComplete="username" />
        </label>
        <label>
          닉네임
          <input name="nickname" value={form.nickname} onChange={onChange} />
        </label>
        <div className="form-row">
          <label>
            나이
            <input name="age" type="number" min="0" value={form.age} onChange={onChange} />
          </label>
          <label>
            성별
            <select name="gender" value={form.gender} onChange={onChange}>
              {GENDERS.map((g) => (
                <option key={g.value} value={g.value}>{g.label}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          비밀번호
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={onChange}
            autoComplete="new-password"
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>
          {loading ? '가입 중…' : '가입하기'}
        </button>
      </form>
      <p className="muted">
        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
      </p>
    </div>
  )
}
