import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading] = useState(false)

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const errs = {}
    if (!loginId.trim()) errs.loginId = '아이디를 입력해주세요'
    if (!password.trim()) errs.password = '비밀번호를 입력해주세요'
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setFieldErrors({})
    setLoading(true)
    try {
      await login(loginId, password)
      navigate('/')
    } catch (err) {
      setError(err.message || '로그인에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-card">
      <h1>로그인</h1>
      <form onSubmit={onSubmit}>
        <label>
          <span className="field-caption">아이디 <span className="req">*</span></span>
          <input
            value={loginId}
            onChange={(e) => {
              setLoginId(e.target.value)
              if (e.target.value.trim()) setFieldErrors((prev) => ({ ...prev, loginId: undefined }))
            }}
            autoComplete="username"
          />
          {fieldErrors.loginId && <p className="field-error">! {fieldErrors.loginId}</p>}
        </label>
        <label>
          <span className="field-caption">비밀번호 <span className="req">*</span></span>
          <input
            type="password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              if (e.target.value.trim()) setFieldErrors((prev) => ({ ...prev, password: undefined }))
            }}
            autoComplete="current-password"
          />
          {fieldErrors.password && <p className="field-error">! {fieldErrors.password}</p>}
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>
          {loading ? '로그인 중…' : '로그인'}
        </button>
      </form>
      <p className="muted">
        계정이 없으신가요? <Link to="/signup">회원가입</Link>
      </p>
    </div>
  )
}
