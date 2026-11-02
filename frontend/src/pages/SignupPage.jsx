import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api, splitApiError } from '../api.js'
import { GENDERS } from '../constants.js'

// 올해부터 1900년까지 내림차순. 서버도 같은 범위만 받는다.
const BIRTH_YEARS = Array.from(
  { length: new Date().getFullYear() - 1900 + 1 },
  (_, i) => new Date().getFullYear() - i,
)

export default function SignupPage() {
  const navigate = useNavigate()
  // birthYear/gender는 가입 시 기본 페르소나 생성에 쓰여 백엔드에서 필수다.
  const [form, setForm] = useState({
    email: '', loginId: '', nickname: '', password: '', birthYear: '', gender: 'MALE',
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

  // 필수 입력(@NotBlank / @NotNull) + 형식(@Email / @Size / @Pattern / @Min) 검증.
  // 형식 규칙과 문구는 백엔드 CreateUserRequest와 맞춘다(같은 규칙이 양쪽에서 같은 말로 읽히도록).
  // 서버도 같은 규칙으로 검사하지만, 요청 전에 걸러 바로 칸을 짚어 준다.
  const validate = () => {
    const errs = {}
    if (!form.email.trim()) errs.email = '이메일을 입력해주세요'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
      errs.email = '이메일 형식이 올바르지 않습니다'

    const loginId = form.loginId.trim()
    if (!loginId) errs.loginId = '아이디를 입력해주세요'
    else if (!/^[a-z0-9]+$/.test(loginId)) errs.loginId = '아이디는 영문 소문자와 숫자만 사용할 수 있습니다'
    else if (loginId.length < 4 || loginId.length > 20) errs.loginId = '아이디는 4~20자여야 합니다'

    if (!form.nickname.trim()) errs.nickname = '닉네임을 입력해주세요'
    else if (form.nickname.trim().length > 20) errs.nickname = '닉네임은 20자 이하여야 합니다'

    if (form.birthYear === '') errs.birthYear = '출생연도를 선택해주세요'

    if (!form.password) errs.password = '비밀번호를 입력해주세요'
    else if (form.password.length < 8 || form.password.length > 64)
      errs.password = '비밀번호는 8~64자여야 합니다'
    else if (!/^[\x21-\x7E]+$/.test(form.password))
      errs.password = '비밀번호는 영문·숫자·특수문자만 쓸 수 있습니다'
    else if (!/[A-Za-z]/.test(form.password) || !/\d/.test(form.password))
      errs.password = '비밀번호는 영문과 숫자를 각각 하나 이상 포함해야 합니다'
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
      // validate()가 trim한 값으로 검사했으니 보낼 때도 trim한 값을 보낸다.
      // select 값은 문자열이라 숫자로 바꿔 보낸다.
      await api.signup({
        ...form,
        email: form.email.trim(),
        loginId: form.loginId.trim(),
        nickname: form.nickname.trim(),
        birthYear: Number(form.birthYear),
      })
      // 가입 성공 → 로그인 페이지로
      navigate('/login')
    } catch (err) {
      // 중복은 서버만 알 수 있으니 이 경로로 가장 자주 온다.
      const { fieldErrors, formError } = splitApiError(err, {
        fields: ['email', 'loginId', 'nickname', 'birthYear', 'password'],
        codes: { DUPLICATE_EMAIL: 'email', DUPLICATE_LOGIN_ID: 'loginId', DUPLICATE_NICKNAME: 'nickname' },
        fallback: '회원가입에 실패했습니다.',
      })
      setFieldErrors(fieldErrors)
      setError(formError)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-card">
      <h1>회원가입</h1>
      {/* noValidate: 브라우저 기본 검증 말풍선 대신 앱 스타일 field-error로 보여준다.
          (네이티브 검증은 submit보다 먼저 돌아 onSubmit 자체를 막는다) */}
      <form onSubmit={onSubmit} noValidate>
        <label>
          <span className="field-caption">이메일 <span className="req">*</span></span>
          <input name="email" type="email" value={form.email} onChange={onChange} />
          {fieldErrors.email && <p className="field-error">! {fieldErrors.email}</p>}
        </label>
        <label>
          <span className="field-caption">아이디 <span className="req">*</span> (영문 소문자·숫자 4~20자)</span>
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
            <span className="field-caption">출생연도 <span className="req">*</span></span>
            <select name="birthYear" value={form.birthYear} onChange={onChange}>
              <option value="">선택</option>
              {BIRTH_YEARS.map((y) => <option key={y} value={y}>{y}년</option>)}
            </select>
            {fieldErrors.birthYear && <p className="field-error">! {fieldErrors.birthYear}</p>}
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
          <span className="field-caption">비밀번호 <span className="req">*</span> (영문·숫자 포함 8~64자, 한글·공백 불가)</span>
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
