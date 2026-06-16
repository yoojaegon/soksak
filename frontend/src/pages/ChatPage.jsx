import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api.js'

export default function ChatPage() {
  const { roomId } = useParams()
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [acting, setActing] = useState(false) // 재생성/삭제/수정 진행 중
  const [editingId, setEditingId] = useState(null) // 현재 수정 중인 메시지 id
  const [editText, setEditText] = useState('')
  const [error, setError] = useState('')
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

  // 메시지가 늘어나면 항상 맨 아래로 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, sending])

  const onSend = async (e) => {
    e.preventDefault()
    const content = input.trim()
    if (!content || sending) return

    setSending(true)
    setError('')

    // 내 메시지는 서버 응답을 기다리지 않고 바로 화면에 표시한다(낙관적 업데이트).
    const tempUser = { id: `temp-${Date.now()}`, role: 'USER', content }
    setMessages((prev) => [...prev, tempUser])
    setInput('')

    try {
      // 서버는 USER 메시지를 저장하고 AI(ASSISTANT) 응답만 돌려준다.
      await api.sendMessage(roomId, content)
      // 임시 메시지를 실제 저장본(id 포함)으로 교체 → 재생성/삭제가 바로 동작
      await reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setSending(false)
    }
  }

  // 마지막 AI 응답을 다시 생성
  const onRegenerate = async () => {
    if (acting || sending) return
    setActing(true)
    setError('')
    try {
      await api.regenerate(roomId)
      await reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setActing(false)
    }
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

  // 재생성 버튼은 "내 마지막 메시지" 아래에만 보이게 한다.
  const lastUserIdx = messages.map((m) => m.role).lastIndexOf('USER')

  return (
    <div className="chat">
      <div className="messages">
        {loading ? (
          <p className="muted">대화를 불러오는 중…</p>
        ) : messages.length === 0 ? (
          <p className="muted">첫 메시지를 보내 대화를 시작해보세요.</p>
        ) : (
          messages.map((m, idx) => {
            const isTemp = typeof m.id === 'string' && m.id.startsWith('temp-')
            const side = m.role === 'USER' ? 'me' : 'bot'
            // 내 마지막 메시지 아래에만 재생성 노출 (응답 대기 중엔 숨김)
            const showRegenerate = idx === lastUserIdx && !isTemp && !sending

            // 수정 중인 메시지는 입력창으로 표시
            if (editingId === m.id) {
              return (
                <div key={m.id} className={`msg-row ${side}`}>
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
              <div key={m.id} className={`msg-row ${side}`}>
                <div className={`bubble ${side}`}>{m.content}</div>
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
        {sending && (
          <div className="msg-row bot">
            <div className="bubble bot typing" aria-label="응답 생성 중">
              <span className="dot" />
              <span className="dot" />
              <span className="dot" />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {error && <p className="error">{error}</p>}

      <form className="composer" onSubmit={onSend}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="메시지를 입력하세요"
        />
        <button type="submit" disabled={sending || !input.trim()}>
          보내기
        </button>
      </form>
    </div>
  )
}
