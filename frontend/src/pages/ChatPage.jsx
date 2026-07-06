import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { api } from '../api.js'

// 메시지 시각을 HH:MM 타임코드로. createdAt이 없으면(임시 메시지) 빈 문자열.
function timecode(createdAt) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

// <spoiler>…</spoiler> 구간은 클릭해서 펼치는 가림막으로, 나머지는 그대로 렌더한다.
function Spoiler({ text }) {
  const [open, setOpen] = useState(false)
  return (
    <span
      className={`spoiler ${open ? 'open' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => setOpen((v) => !v)}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setOpen((v) => !v)}
      title={open ? '가리기' : '펼쳐 보기'}
    >
      {text}
    </span>
  )
}

function MessageLine({ content }) {
  const regex = /<spoiler>([\s\S]*?)<\/spoiler>/g
  const parts = []
  let last = 0
  let m
  let key = 0
  while ((m = regex.exec(content)) !== null) {
    if (m.index > last) parts.push(content.slice(last, m.index))
    parts.push(<Spoiler key={`sp-${key++}`} text={m[1]} />)
    last = regex.lastIndex
  }
  if (last < content.length) parts.push(content.slice(last))
  return <div className="line">{parts}</div>
}

// 방(roomId)이 바뀌면 통째로 새로 마운트해, 이전 방의 지연 응답이 새 방 화면을
// 덮어쓰지 않도록 한다. 아래 ChatRoom이 실제 대화 화면.
export default function ChatPage() {
  const { roomId } = useParams()
  return <ChatRoom key={roomId} roomId={roomId} />
}

function ChatRoom({ roomId }) {
  const [character, setCharacter] = useState(null)
  const [messages, setMessages] = useState([])
  const [config, setConfig] = useState({ writingToggle: false, foldSpoilerToggle: false })
  // config를 바꿀 땐 ref도 함께 맞춰, 빠른 연속 토글에서도 최신값을 동기적으로 읽는다.
  const configRef = useRef(config)
  const writeConfig = (next) => {
    configRef.current = next
    setConfig(next)
  }
  const [showSettings, setShowSettings] = useState(false)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [acting, setActing] = useState(false) // 재생성/삭제/수정 진행 중
  const [editingId, setEditingId] = useState(null) // 현재 수정 중인 메시지 id
  const [editText, setEditText] = useState('')
  const [error, setError] = useState('')
  // 방 자체를 불러오지 못하면(삭제·잘못된 id) 빈 화면 대신 명확히 안내한다.
  const [roomError, setRoomError] = useState('')
  const bottomRef = useRef(null)

  // 서버에서 대화 내역을 다시 받아와 상태를 맞춘다.
  const reload = async () => {
    const data = await api.getMessages(roomId)
    setMessages(data ?? [])
  }

  // 방에 들어오면 기존 대화 내역을 불러온다.
  useEffect(() => {
    let alive = true
    api
      .getMessages(roomId)
      .then((data) => {
        if (alive) setMessages(data ?? [])
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
  }, [roomId])

  // 방 정보(설정 토글) + 헤더에 표시할 캐릭터 정보(이름·소개)를 조회한다.
  useEffect(() => {
    let alive = true
    api
      .getChatRoom(roomId)
      .then((room) => {
        if (!alive) return
        writeConfig({
          writingToggle: !!room.writingToggle,
          foldSpoilerToggle: !!room.foldSpoilerToggle,
        })
        // 캐릭터(헤더) 정보 실패는 대화를 막지 않도록 조용히 무시한다.
        api
          .getCharacter(room.characterId)
          .then((c) => {
            if (alive) setCharacter(c)
          })
          .catch(() => {})
      })
      .catch((err) => {
        // 방 자체를 못 불러오면(삭제·잘못된 id) 빈 화면 대신 안내를 띄운다.
        if (alive) setRoomError(err.message || '대화방을 찾을 수 없어요.')
      })
    return () => {
      alive = false
    }
  }, [roomId])

  // 메시지가 늘어나면 항상 맨 아래로 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  // 설정 토글 변경: 화면을 먼저 바꾸고(낙관적) 서버에 반영, 실패하면 되돌린다.
  const toggleConfig = async (key) => {
    const prev = configRef.current[key] // 요청 전 값을 저장해 두었다가 실패 시 그대로 복원
    const next = { ...configRef.current, [key]: !prev }
    writeConfig(next)
    try {
      await api.updateConfig(roomId, next)
    } catch (err) {
      // 실패한 토글만 요청 전 값으로 되돌린다(그새 바뀐 다른 토글의 최신값은 보존)
      writeConfig({ ...configRef.current, [key]: prev })
      setError(err.message)
    }
  }

  const onSend = async (e) => {
    e.preventDefault()
    const content = input.trim()
    if (!content || sending) return

    setSending(true)
    setError('')

    // 내 메시지와 빈 AI 버블을 먼저 화면에 올린다(낙관적). AI 버블엔 토큰이 도착하는 대로 이어붙인다.
    const now = Date.now()
    const tempUser = { id: `temp-${now}`, role: 'USER', content }
    const aiId = `temp-ai-${now}`
    setMessages((prev) => [...prev, tempUser, { id: aiId, role: 'ASSISTANT', content: '' }])
    setInput('')

    let failed = null
    await api.sendMessageStream(roomId, content, {
      // 토큰 도착 → 해당 AI 버블에 이어붙인다.
      onToken: (t) =>
        setMessages((prev) => prev.map((m) => (m.id === aiId ? { ...m, content: m.content + t } : m))),
      onDone: () => {},
      onError: (err) => {
        failed = err
      },
    })

    if (failed) {
      // 실패/중단 → 낙관적으로 넣었던 임시 버블들을 제거하고 입력값을 되돌린다(유령 메시지 방지).
      setMessages((prev) => prev.filter((m) => m.id !== tempUser.id && m.id !== aiId))
      setInput(content)
      setError(failed.message)
    } else {
      // 임시 버블들을 실제 저장본(id·시각 포함)으로 교체 → 재생성/수정/삭제가 바로 동작.
      // reload가 실패해도(토큰 만료·네트워크 순단) 화면은 스트리밍으로 이미 채워져 있으니,
      // 에러만 표시하고 sending이 true로 멈춰버리는 상황은 피한다.
      try {
        await reload()
      } catch (err) {
        setError(err.message)
      }
    }
    setSending(false)
  }

  // 마지막 AI 응답을 다시 생성(스트리밍)
  const onRegenerate = async () => {
    if (acting || sending) return
    setActing(true)
    setError('')

    // 마지막 AI 버블을 화면에서 비우고, 그 자리에 새 응답을 스트리밍한다.
    const aiId = `temp-ai-${Date.now()}`
    setMessages((prev) => {
      const base = prev[prev.length - 1]?.role === 'ASSISTANT' ? prev.slice(0, -1) : prev
      return [...base, { id: aiId, role: 'ASSISTANT', content: '' }]
    })

    let failed = null
    await api.regenerateStream(roomId, {
      onToken: (t) =>
        setMessages((prev) => prev.map((m) => (m.id === aiId ? { ...m, content: m.content + t } : m))),
      onDone: () => {},
      onError: (err) => {
        failed = err
      },
    })

    // 성공이든 실패든 서버 상태로 동기화(재생성은 서버에서 이전 답을 이미 지웠을 수 있음).
    // reload가 실패해도 acting이 true로 멈추지 않도록 감싼다. 표시할 스트림 에러가 없으면
    // reload 에러라도 보여준다.
    try {
      await reload()
    } catch (err) {
      if (!failed) failed = err
    }
    if (failed) setError(failed.message)
    setActing(false)
  }

  // 메시지 수정 시작 / 저장 / 취소
  const startEdit = (m) => {
    setEditingId(m.id)
    setEditText(m.content)
  }
  const cancelEdit = () => {
    setEditingId(null)
    setEditText('')
  }
  const saveEdit = async () => {
    const content = editText.trim()
    if (!content) return
    setActing(true)
    setError('')
    try {
      await api.updateMessage(roomId, editingId, content)
      await reload()
      cancelEdit()
    } catch (err) {
      setError(err.message)
    } finally {
      setActing(false)
    }
  }

  // 이 메시지부터 이후 대화를 모두 삭제
  const onDeleteFrom = async (messageId) => {
    if (acting || sending) return
    if (!window.confirm('이 메시지부터 이후 대화가 모두 삭제됩니다. 계속할까요?')) return
    setActing(true)
    setError('')
    try {
      await api.deleteFrom(roomId, messageId)
      await reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setActing(false)
    }
  }

  // 방을 불러오지 못했으면 빈 대화 화면 대신 명확한 안내를 보여준다.
  if (roomError) {
    return (
      <div className="chat">
        <div className="chat-main">
          <p className="error">{roomError}</p>
          <p className="muted"><Link to="/">← 홈으로 돌아가기</Link></p>
        </div>
      </div>
    )
  }

  // 재생성 버튼은 "내 마지막 메시지" 아래에만 보이게 한다.
  const lastUserIdx = messages.map((m) => m.role).lastIndexOf('USER')

  return (
    <div className={`chat ${showSettings ? 'with-aside' : ''}`}>
      <div
        className="chat-main"
        onClick={() => showSettings && setShowSettings(false)}
      >
        <div className="lb" />

        {character && (
          <header className="chat-head">
            <div className="chat-head-main">
              {character.userName && <span className="cap">@{character.userName} 제작</span>}
              <h1 className="ch-name">{character.characterName}</h1>
              {character.description && <p className="ch-sub">{character.description}</p>}
            </div>
            {sending && (
              <span className="now-speaking">
                <span className="dot" />
                NOW SPEAKING
              </span>
            )}
            <button
              type="button"
              className={`gear-btn ${showSettings ? 'on' : ''}`}
              onClick={(e) => {
                e.stopPropagation()
                setShowSettings((v) => !v)
              }}
              aria-expanded={showSettings}
              aria-label="대화 설정"
              title="대화 설정"
            >
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="3" />
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
              </svg>
            </button>
          </header>
        )}

      <div className="conv">
        {loading ? (
          <p className="muted">대화를 불러오는 중…</p>
        ) : messages.length === 0 ? (
          <p className="muted">첫 메시지를 보내 대화를 시작해보세요.</p>
        ) : (
          messages.map((m, idx) => {
            const isTemp = typeof m.id === 'string' && m.id.startsWith('temp-')
            const side = m.role === 'USER' ? 'me' : 'them'
            const who = m.role === 'USER' ? '나' : character?.characterName ?? '캐릭터'
            // 내 마지막 메시지 아래에만 재생성 노출 (응답 대기 중엔 숨김)
            const showRegenerate = idx === lastUserIdx && !isTemp && !sending

            // 수정 중인 메시지는 입력창으로 표시
            if (editingId === m.id) {
              return (
                <div key={m.id} className={`turn ${side}`}>
                  <div className="who">
                    <span className="name">{who}</span>
                  </div>
                  <div className="edit-box">
                    <textarea
                      value={editText}
                      onChange={(e) => setEditText(e.target.value)}
                      rows={3}
                      autoFocus
                    />
                    <div className="msg-actions">
                      <button className="msg-action" onClick={saveEdit} disabled={acting || !editText.trim()}>
                        저장
                      </button>
                      <button className="msg-action" onClick={cancelEdit} disabled={acting}>
                        취소
                      </button>
                    </div>
                  </div>
                </div>
              )
            }

            return (
              <div key={m.id} className={`turn ${side}`}>
                <div className="who">
                  <span className="name">{who}</span>
                  {timecode(m.createdAt) && <span className="tc">{timecode(m.createdAt)}</span>}
                </div>
                {isTemp && m.role === 'ASSISTANT' && m.content === '' ? (
                  // 아직 첫 토큰이 안 온 스트리밍 버블 → 타이핑 점 표시
                  <div className="typing" aria-label="응답 생성 중">
                    <span />
                    <span />
                    <span />
                  </div>
                ) : (
                  <MessageLine content={m.content} />
                )}
                {!isTemp && (
                  <div className="msg-actions">
                    {showRegenerate && (
                      <button className="msg-action" onClick={onRegenerate} disabled={acting || sending}>
                        재생성
                      </button>
                    )}
                    <button className="msg-action" onClick={() => startEdit(m)} disabled={acting || sending}>
                      수정
                    </button>
                    <button className="msg-action" onClick={() => onDeleteFrom(m.id)} disabled={acting || sending}>
                      삭제
                    </button>
                  </div>
                )}
              </div>
            )
          })
        )}
        <div ref={bottomRef} />
      </div>

      {error && <p className="error">{error}</p>}

      <form className="composer" onSubmit={onSend}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="당신의 대사나 행동을 적으세요…"
        />
        <button type="submit" disabled={sending || !input.trim()}>
          전하기
        </button>
      </form>

        <div className="lb" />
      </div>

      {showSettings && (
        <aside className="chat-aside">
          <div className="aside-head">
            <span className="cap">대화 설정</span>
            <button
              type="button"
              className="aside-close"
              onClick={() => setShowSettings(false)}
              aria-label="설정 닫기"
            >
              ✕
            </button>
          </div>
          <div className="seg">
            <button type="button" className="tog" onClick={() => toggleConfig('foldSpoilerToggle')}>
              <span className="tog-l">
                스포일러 가리기
                <span className="tog-hint">내 시점 밖 내용을 가림막으로</span>
              </span>
              <span className={`sw ${config.foldSpoilerToggle ? 'on' : ''}`} />
            </button>
            <button type="button" className="tog" onClick={() => toggleConfig('writingToggle')}>
              <span className="tog-l">
                글쓰기 모드
                <span className="tog-hint">캐릭터가 장면을 주도적으로 전개</span>
              </span>
              <span className={`sw ${config.writingToggle ? 'on' : ''}`} />
            </button>
          </div>
        </aside>
      )}
    </div>
  )
}
