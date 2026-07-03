import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { GENDERS } from '../constants.js'

export default function SignupPage() {
  const navigate = useNavigate()
  // age/gender는 가입 시 기본 페르소나 생성에 쓰여 백엔드에서 필수다.
  const [form, setForm] = useState({
    email: '', loginId: '', nickname: '', password: '', age: '', gender: 'MALE',
  })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading] = useState(false)

  const onChange = (e) => {
    const { name, value } = e.target
    setForm({ ...form, [name]: value })
    // 값을 채우면 그 필드의 필수 에러는 지운다.
    if (value.trim()) setFieldErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  // 필수 입력(@NotBlank / @NotNull) 검증
  const validate = () => {
    const errs = {}
    if (!form.email.trim()) errs.email = '이메일을 입력해주세요'
    if (!form.loginId.trim()) errs.loginId = '아이디를 입력해주세요'
    if (!form.nickname.trim()) errs.nickname = '닉네임을 입력해주세요'
    if (form.age === '') errs.age = '나이를 입력해주세요'
    if (!form.password.trim()) errs.password = '비밀번호를 입력해주세요'
    return errs
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const errs = validate()
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs)
      return
    }
    setFieldErrors({})
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
          <span className="field-caption">이메일 <span className="req">*</span></span>
          <input name="email" type="email" value={form.email} onChange={onChange} />
          {fieldErrors.email && <p className="field-error">! {fieldErrors.email}</p>}
        </label>
        <label>
          <span className="field-caption">아이디 <span className="req">*</span> (영문·숫자 4~20자)</span>
          <input name="loginId" value={form.loginId} onChange={onChange} autoComplete="username" />
          {fieldErrors.loginId && <p className="field-error">! {fieldErrors.loginId}</p>}
        </label>
        <label>
          <span className="field-caption">닉네임 <span className="req">*</span></span>
          <input name="nickname" value={form.nickname} onChange={onChange} />
          {fieldErrors.nickname && <p className="field-error">! {fieldErrors.nickname}</p>}
        </label>
        <div className="form-row">
          <label>
            <span className="field-caption">나이 <span className="req">*</span></span>
            <input name="age" type="number" min="0" value={form.age} onChange={onChange} />
            {fieldErrors.age && <p className="field-error">! {fieldErrors.age}</p>}
          </label>
          <label>
            <span className="field-caption">성별 <span className="req">*</span></span>
            <select name="gender" value={form.gender} onChange={onChange}>
              {GENDERS.map((g) => (
                <option key={g.value} value={g.value}>{g.label}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          <span className="field-caption">비밀번호 <span className="req">*</span></span>
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={onChange}
            autoComplete="new-password"
          />
          {fieldErrors.password && <p className="field-error">! {fieldErrors.password}</p>}
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
