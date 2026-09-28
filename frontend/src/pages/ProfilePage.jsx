import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useAlert } from '../confirm.jsx'

// 내 정보 — 닉네임 수정 + 비밀번호 변경.
// 계정에 있는 나머지(나이·성별)는 GET /users/me가 내려주지 않고, 가입 때 기본 페르소나를 만드는 데만
// 쓰인 값이라 여기서 다루지 않는다.
export default function ProfilePage() {
  const [me, setMe] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [nickname, setNickname] = useState('')
  const [nicknameError, setNicknameError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)
  // 비밀번호 창을 닫으면 여기로 포커스를 돌린다. 이 화면에선 항상 렌더돼 있다(confirm-focus-landing).
  const pwButtonRef = useRef(null)
  const { logout } = useAuth()
  const alert = useAlert()
  const navigate = useNavigate()

  useEffect(() => {
    let alive = true
    api
      .getMe()
      .then((data) => {
        if (!alive) return
        setMe(data)
        setNickname(data?.nickname ?? '')
      })
      .catch((err) => alive && setLoadError(err.message || '내 정보를 불러오지 못했습니다.'))
    return () => {
      alive = false
    }
  }, [])

  const trimmed = nickname.trim()
  const unchanged = trimmed === (me?.nickname ?? '')

  const saveNickname = async (e) => {
    e.preventDefault()
    if (!trimmed) return setNicknameError('닉네임을 입력해주세요')
    if (trimmed.length > 20) return setNicknameError('닉네임은 20자 이하여야 합니다')
    if (unchanged) return
    setSaving(true)
    setNicknameError('')
    try {
      const updated = await api.updateMe(trimmed)
      setMe(updated)
      setNickname(updated.nickname)
      setSaved(true)
      // 상단바 계정 메뉴가 들고 있는 닉네임을 갱신시킨다.
      window.dispatchEvent(new Event('soksak:me-changed'))
    } catch (err) {
      setNicknameError(err.message || '저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const closePw = () => setPwOpen(false)

  // 서버가 이 계정의 refresh 토큰을 지웠다(D14). 그대로 두면 몇 분 뒤 access가 만료되는 순간
  // 갑자기 튕기므로, 지금 알리고 정리한다. 알림은 확인 다이얼로그(#root를 inert로 덮는다)라
  // 비밀번호 창을 먼저 닫아야 둘이 겹치지 않는다.
  const onPasswordChanged = async () => {
    closePw()
    await alert({ title: '비밀번호를 바꿨어요', message: '새 비밀번호로 다시 로그인해 주세요.' })
    await logout()
    navigate('/login', { replace: true })
  }

  if (loadError) return <p className="error">{loadError}</p>
  if (!me) return <p className="muted">불러오는 중…</p>

  return (
    <div className="profile">
      <div className="page-head">
        <h1>내 정보</h1>
      </div>

      <section className="form-card profile-card">
        <h2>프로필</h2>
        <form onSubmit={saveNickname} noValidate>
          <div className="profile-field">
            <label htmlFor="profile-nickname">닉네임</label>
            <div className="profile-inline">
              <input
                id="profile-nickname"
                value={nickname}
                maxLength={20}
                onChange={(e) => {
                  setNickname(e.target.value)
                  setNicknameError('')
                  setSaved(false)
                }}
              />
              <button type="submit" disabled={saving || unchanged}>
                {saving ? '저장 중…' : '저장'}
              </button>
            </div>
            {nicknameError ? (
              <p className="field-error">! {nicknameError}</p>
            ) : (
              <p className="field-hint">
                {saved ? '저장했어요.' : '20자 이내. 대화 속 기본 페르소나 이름은 따로 바뀌지 않아요.'}
              </p>
            )}
          </div>
        </form>
        <dl className="profile-rows">
          <div><dt>아이디</dt><dd>{me.loginId}</dd></div>
          <div><dt>이메일</dt><dd>{me.email}</dd></div>
          <div><dt>가입일</dt><dd>{formatDate(me.createdAt)}</dd></div>
        </dl>
      </section>

      <section className="form-card profile-card">
        <h2>보안</h2>
        <div className="profile-security">
          <div>
            <strong>비밀번호</strong>
            <span>바꾸면 다시 로그인해야 해요.</span>
          </div>
          <button type="button" className="btn-ghost" ref={pwButtonRef} onClick={() => setPwOpen(true)}>
            비밀번호 변경
          </button>
        </div>
      </section>

      {pwOpen && (
        <PasswordDialog onClose={closePw} onChanged={onPasswordChanged} returnFocusRef={pwButtonRef} />
      )}
    </div>
  )
}

function formatDate(iso) {
  if (!iso) return '–'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '–'
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

// 비밀번호 변경 창. confirm.jsx는 버튼만 받는 다이얼로그라, 입력칸이 있는 이 창은 네이티브 <dialog>로 띄운다
// — showModal()이 포커스 가두기·뒤 페이지 비활성화·Esc를 브라우저 차원에서 해 준다.
function PasswordDialog({ onClose, onChanged, returnFocusRef }) {
  const dialogRef = useRef(null)
  const downOnBackdrop = useRef(false)
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog.showModal()
    return () => {
      dialog.close()
      // 모달이 떠 있는 동안엔 바깥 요소가 inert라 focus()가 먹지 않는다. 닫은 다음에 돌려준다.
      returnFocusRef.current?.focus()
    }
  }, [returnFocusRef])

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  // 형식 규칙과 문구는 백엔드 ChangePasswordRequest(=회원가입과 같은 규칙)에 맞춘다.
  // 서버는 형식 오류를 필드 구분 없이 INVALID_INPUT 하나로 돌려주므로 여기서 걸러야 칸을 짚어 줄 수 있다.
  const validate = () => {
    const errs = {}
    if (!form.current) errs.current = '현재 비밀번호를 입력해주세요'
    if (!form.next) errs.next = '새 비밀번호를 입력해주세요'
    else if (form.next.length < 8 || form.next.length > 64) errs.next = '비밀번호는 8~64자여야 합니다'
    else if (!/[A-Za-z]/.test(form.next) || !/\d/.test(form.next))
      errs.next = '비밀번호는 영문과 숫자를 각각 하나 이상 포함해야 합니다'
    // 확인 칸은 서버로 보내지 않는다 — 오타 방지용이라 화면에서만 비교한다.
    if (!errs.next && form.confirm !== form.next) errs.confirm = '새 비밀번호와 같게 입력해주세요'
    return errs
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    const errs = validate()
    if (Object.keys(errs).length > 0) return setErrors(errs)
    setSubmitting(true)
    try {
      await api.changePassword(form.current, form.next)
    } catch (err) {
      setSubmitting(false)
      if (err.code === 'INVALID_CURRENT_PASSWORD') return setErrors({ current: err.message })
      if (err.code === 'SAME_AS_CURRENT_PASSWORD') return setErrors({ next: err.message })
      return setError(err.message || '비밀번호를 바꾸지 못했습니다.')
    }
    onChanged()
  }

  return (
    <dialog
      ref={dialogRef}
      className="pw-dialog"
      aria-labelledby="pw-title"
      // Esc: 브라우저가 스스로 닫기 전에 가로채서 React 상태로 닫는다(안 그러면 상태는 열림인데 창만 사라진다).
      onCancel={(e) => {
        e.preventDefault()
        if (!submitting) onClose()
      }}
      // 위 preventDefault는 항상 먹히지 않는다 — 크롬은 사용자 조작 없이 Esc가 연달아 오면 두 번째부터
      // 취소 불가능한 cancel을 보내고 창을 닫아 버린다. 그때 상태도 따라 닫아야 "열림인데 안 보이는" 창이
      // 남지 않는다. open 확인은 StrictMode의 close→showModal 재실행 뒤에 늦게 도착한 close를 거르는 용도.
      onClose={(e) => {
        if (!e.currentTarget.open) onClose()
      }}
      // 바깥(::backdrop) 클릭은 dialog 자신이 target이 된다. 누르기도 거기서 시작했을 때만 닫는다.
      onMouseDown={(e) => {
        downOnBackdrop.current = e.target === e.currentTarget
      }}
      onClick={(e) => {
        if (downOnBackdrop.current && e.target === e.currentTarget && !submitting) onClose()
      }}
    >
      <div className="confirm-bar" />
      <form className="pw-body" onSubmit={onSubmit} noValidate>
        <div>
          <h2 className="confirm-title" id="pw-title">비밀번호 변경</h2>
          <p className="confirm-msg">바꾸고 나면 로그아웃돼요. 새 비밀번호로 다시 로그인해 주세요.</p>
        </div>
        <label>
          <span className="field-caption">현재 비밀번호</span>
          <input type="password" name="current" autoComplete="current-password" autoFocus
            value={form.current} onChange={onChange} aria-invalid={!!errors.current} />
          {errors.current && <p className="field-error">! {errors.current}</p>}
        </label>
        <label>
          <span className="field-caption">새 비밀번호</span>
          <input type="password" name="next" autoComplete="new-password"
            value={form.next} onChange={onChange} aria-invalid={!!errors.next} />
          {errors.next
            ? <p className="field-error">! {errors.next}</p>
            : <p className="field-hint">8~64자, 영문과 숫자를 하나 이상씩</p>}
        </label>
        <label>
          <span className="field-caption">새 비밀번호 확인</span>
          <input type="password" name="confirm" autoComplete="new-password"
            value={form.confirm} onChange={onChange} aria-invalid={!!errors.confirm} />
          {errors.confirm && <p className="field-error">! {errors.confirm}</p>}
        </label>
        {error && <p className="error">{error}</p>}
        <div className="confirm-actions pw-actions">
          <button type="button" className="ghost" onClick={onClose} disabled={submitting}>취소</button>
          <button type="submit" disabled={submitting}>{submitting ? '바꾸는 중…' : '변경하기'}</button>
        </div>
      </form>
      <div className="confirm-bar" />
    </dialog>
  )
}
