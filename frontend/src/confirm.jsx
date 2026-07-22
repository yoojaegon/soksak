// 앱 공용 확인/알림 다이얼로그. window.confirm·alert이 브라우저 기본 창으로 떠서 디자인이 끊기는 걸 대체한다.
//
// 둘 다 동기라 호출부가 `if (!confirm(...)) return` 한 줄로 끝났는데, 그 모양을 그대로 유지하려고
// Promise를 돌려주는 훅으로 만들었다. 덕분에 호출부는 await 하나만 붙이면 되고, 각 화면이
// "다이얼로그 열림" 상태를 따로 들고 있을 필요가 없다.
//
//   const confirm = useConfirm()
//   if (!(await confirm({ title: '삭제할까요?', danger: true }))) return
//
//   const alert = useAlert()
//   await alert({ title: '일부만 저장됐어요' })   // 확인 버튼 하나. 누를 때까지 다음 줄로 안 넘어간다
//
// 이미 떠 있는데 또 부르면 줄을 세운다. window.confirm과 달리 호출부가 await로 멈춰 있는 동안에도
// 화면은 계속 살아 있어서(비동기 작업이 끝나며 alert을 부르는 등) 겹치는 일이 실제로 생기는데,
// 나중 것으로 덮어쓰면 앞 호출의 Promise가 영영 안 풀려 그 호출부가 멈춘 채로 남는다.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const ConfirmContext = createContext(null)

export function ConfirmProvider({ children }) {
  // 지금 떠 있는 요청 { id, opts, resolve }. null이면 닫힌 상태.
  const [current, setCurrent] = useState(null)
  // 대기 중인 요청들. 렌더와 무관하게 즉시 읽고 써야 해서 ref로 둔다.
  const queueRef = useRef([])
  // setCurrent는 즉시 반영되지 않아, 한 틱에 두 번 열릴 때 최신 상태를 ref로 같이 들고 본다.
  const currentRef = useRef(null)
  const idRef = useRef(0)

  const open = useCallback(
    (opts) =>
      new Promise((resolve) => {
        const req = { id: (idRef.current += 1), opts, resolve }
        if (currentRef.current) {
          queueRef.current.push(req)   // 이미 떠 있으면 줄만 세우고, 닫힐 때 settle이 이어받는다
          return
        }
        currentRef.current = req
        setCurrent(req)
      }),
    [],
  )

  // notice = 취소 없이 확인 버튼 하나(알림). 취소 경로가 없을 뿐 나머지는 확인 다이얼로그와 같다.
  const value = useMemo(
    () => ({
      confirm: open,
      alert: (opts) => open({ ...opts, notice: true }),
    }),
    [open],
  )

  // 답이 정해지면 닫고 대기 중인 호출부를 깨운다. 줄 서 있는 게 있으면 이어서 연다.
  const settle = useCallback((answer) => {
    const done = currentRef.current
    const next = queueRef.current.shift() ?? null
    currentRef.current = next
    setCurrent(next)
    done?.resolve(answer)
  }, [])

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {current && <ConfirmDialog key={current.id} options={current.opts} onSettle={settle} />}
    </ConfirmContext.Provider>
  )
}

// provider 밖에서 부르면 컨텍스트가 null이라 원인 모를 TypeError가 난다. 무엇이 빠졌는지 말해준다.
function useDialog() {
  const ctx = useContext(ConfirmContext)
  if (!ctx) throw new Error('useConfirm/useAlert은 <ConfirmProvider> 안에서만 쓸 수 있습니다.')
  return ctx
}

export function useConfirm() {
  return useDialog().confirm
}

export function useAlert() {
  return useDialog().alert
}

function ConfirmDialog({ options, onSettle }) {
  const {
    title,
    message,
    confirmLabel = '확인',
    cancelLabel = '취소',
    danger = false,
    notice = false,
  } = options
  const panelRef = useRef(null)
  // 백드롭은 "누르기도 여기서 시작했을 때"만 닫는다. 패널 안에서 드래그해 밖에서 뗀 경우를 거른다.
  const downOnBackdrop = useRef(false)

  // 떠 있는 동안 뒤 페이지를 얼린다: 스크롤 잠금(window.confirm이 해주던 것) + inert.
  // aria-modal은 선언일 뿐이라 스크린리더·브라우저 찾기는 뒤 내용을 계속 훑는다. inert는 포커스·
  // 클릭·접근성 트리에서 서브트리를 통째로 뺀다. React 18은 inert를 prop으로 못 받아 DOM에 직접 꽂고,
  // 그래서 다이얼로그 자신은 portal로 #root 밖(body)에 그린다 — 안에 있으면 같이 죽는다.
  //
  // ※ 이 effect는 반드시 아래 포커스 effect보다 먼저 선언돼 있어야 한다. cleanup은 선언 순서대로
  //   돌기 때문에, 순서가 바뀌면 #root가 아직 inert인 상태에서 포커스를 되돌리려다 조용히 실패한다.
  useEffect(() => {
    const root = document.getElementById('root')
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    if (root) root.inert = true
    return () => {
      document.body.style.overflow = prevOverflow
      if (root) root.inert = false
    }
  }, [])

  // 열릴 때 첫 버튼에 포커스를 준다. 확인 다이얼로그에선 그게 '취소'라, 되돌릴 수 없는 동작에서
  // Enter가 실행이 아닌 취소로 간다(알림은 버튼이 하나뿐이라 그 버튼). 닫을 땐 원래 포커스를
  // 되돌려 키보드 사용자가 맥락을 잃지 않게 한다(그 사이 사라진 요소면 되돌릴 곳이 없으니 건너뛴다).
  useEffect(() => {
    const previous = document.activeElement
    panelRef.current?.querySelector('button')?.focus()
    return () => {
      if (previous?.isConnected) previous.focus?.()
    }
  }, [])

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') {
        onSettle(false)
        return
      }
      if (e.key !== 'Tab') return
      // 포커스를 다이얼로그 안에 가둔다(버튼 두 개뿐이라 순환만 시키면 된다).
      const buttons = panelRef.current?.querySelectorAll('button')
      if (!buttons?.length) return
      const first = buttons[0]
      const last = buttons[buttons.length - 1]
      // 패널 여백을 클릭하면 포커스가 body로 빠지는데, 그대로 Tab하면 뒤 페이지로 넘어간다. 끌어온다.
      if (!panelRef.current.contains(document.activeElement)) {
        e.preventDefault()
        ;(e.shiftKey ? last : first).focus()
        return
      }
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onSettle])

  // #root를 inert로 덮으므로 다이얼로그는 그 밖(body)에 그린다.
  return createPortal(
    // 바깥을 누르면 취소. mousedown이 아니라 click에서 닫아야 뒤 요소로 클릭이 새지 않는다
    // (mousedown에 언마운트하면 mouseup이 아래 요소에 떨어져 그쪽 onClick이 딸려 실행된다).
    <div
      className="confirm-backdrop"
      onMouseDown={(e) => {
        downOnBackdrop.current = e.target === e.currentTarget
      }}
      onClick={(e) => {
        if (downOnBackdrop.current && e.target === e.currentTarget) onSettle(false)
      }}
    >
      <div
        className="confirm-panel"
        ref={panelRef}
        // 여백을 클릭해도 포커스가 body로 빠지지 않게(포커스 트랩이 새는 통로).
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
      >
        <div className="confirm-bar" />
        <div className="confirm-body">
          <span className="cap">{notice ? '알림' : danger ? '삭제 확인' : '확인'}</span>
          <h2 className="confirm-title" id="confirm-title">{title}</h2>
          {message && <p className="confirm-msg">{message}</p>}
        </div>
        <div className="confirm-actions">
          {!notice && (
            <button type="button" className="ghost" onClick={() => onSettle(false)}>
              {cancelLabel}
            </button>
          )}
          <button type="button" className={danger ? 'danger' : ''} onClick={() => onSettle(true)}>
            {confirmLabel}
          </button>
        </div>
        <div className="confirm-bar" />
      </div>
    </div>,
    document.body,
  )
}
