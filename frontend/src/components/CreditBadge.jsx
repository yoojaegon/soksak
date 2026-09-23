import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'

// 남은 마디(소모 재화). 채팅 입력칸 왼쪽에 붙어 있고, 누르면 충전 화면으로 간다.
// - 전송이 끝날 때마다 값이 바뀌므로, 화면 곳곳에서 props로 실어 나르는 대신
//   'soksak:credits-changed' 이벤트를 듣고 스스로 다시 불러온다(사이드바의 rooms-changed와 같은 방식).
// - 조회에 실패하면 아무것도 그리지 않는다 — 잔액을 못 읽는 게 대화를 막을 이유는 아니고,
//   진짜 부족은 서버가 402로 알려 준다.
export default function CreditBadge() {
  const [balance, setBalance] = useState(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const data = await api.getCredits()
        if (alive) setBalance(data?.balance ?? null)
      } catch {
        if (alive) setBalance(null)
      }
    }
    load()
    window.addEventListener('soksak:credits-changed', load)
    return () => {
      alive = false
      window.removeEventListener('soksak:credits-changed', load)
    }
  }, [])

  if (balance === null) return null

  return (
    <Link
      to="/credits"
      className={`credit-badge${balance === 0 ? ' empty' : ''}`}
      title={balance === 0 ? '마디를 다 썼어요 — 눌러서 충전' : '남은 마디 — 눌러서 충전'}
    >
      💬 {balance}
    </Link>
  )
}
