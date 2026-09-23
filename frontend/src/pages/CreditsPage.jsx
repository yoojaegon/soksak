import { useEffect, useState } from 'react'
import { api } from '../api.js'

// 마디 충전.
// ⚠️ 결제는 만들지 않는다 — 고르면 그 자리에서 지급된다. 원래 결제로 넘어갈 자리를
//    비워 둔 것이라, 화면에도 그렇게 적어 둔다(가격은 표시일 뿐 받지 않는다).
// 묶음 목록은 백엔드가 단일 출처다(ModelPicker가 /models만 믿는 것과 같다).
export default function CreditsPage() {
  const [packs, setPacks] = useState([])
  const [balance, setBalance] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // 지급 진행 중인 묶음 id (버튼 하나만 잠그려고 boolean이 아니라 id로 둔다)
  const [buyingId, setBuyingId] = useState(null)
  // 방금 받은 양 — 눌렀다는 사실이 화면에 남아야 "된 건가?"가 안 생긴다.
  const [received, setReceived] = useState(null)

  useEffect(() => {
    let alive = true
    Promise.all([api.getCreditPacks(), api.getCredits()])
      .then(([packList, credits]) => {
        if (!alive) return
        setPacks(packList ?? [])
        setBalance(credits?.balance ?? 0)
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
  }, [])

  const onBuy = async (pack) => {
    if (buyingId) return
    setBuyingId(pack.id)
    setError('')
    try {
      const data = await api.topUpCredits(pack.id)
      setBalance(data?.balance ?? balance)
      setReceived(pack.amount)
      // 채팅 화면의 배지가 듣고 스스로 갱신한다.
      window.dispatchEvent(new Event('soksak:credits-changed'))
    } catch (err) {
      setError(err.message)
    }
    setBuyingId(null)
  }

  return (
    <div>
      <div className="page-head">
        <h1>마디 충전</h1>
        {balance !== null && (
          <span className="credit-balance-now">
            지금 <strong>{balance}</strong>마디
          </span>
        )}
      </div>

      <p className="credits-note">
        <strong>결제는 붙어 있지 않아요.</strong> 원래는 여기서 결제로 넘어가지만, 지금은 고르면
        그 자리에서 바로 들어옵니다. 가격은 보여주기용이라 청구되지 않아요.
      </p>

      {error && <p className="error">{error}</p>}
      {received !== null && (
        <p className="notice">
          <span>{received}마디를 받았어요. 이어서 대화할 수 있습니다.</span>
        </p>
      )}

      {loading ? (
        <p className="muted">불러오는 중…</p>
      ) : (
        <div className="pack-grid">
          {packs.map((pack) => (
            <div className="pack-card" key={pack.id}>
              <span className="pack-label">{pack.label}</span>
              <strong className="pack-amount">{pack.amount}마디</strong>
              <span className="pack-price">₩{pack.priceKrw.toLocaleString()}</span>
              <button
                type="button"
                onClick={() => onBuy(pack)}
                disabled={buyingId !== null}
              >
                {buyingId === pack.id ? '받는 중…' : '바로 받기'}
              </button>
            </div>
          ))}
        </div>
      )}

      <p className="muted credits-foot">
        마디는 메시지를 보내거나 응답을 다시 받을 때 줄어들고, 값은 방에서 고른 모델에 따라
        1~3마디예요. 답이 끝내 오지 않은 경우에는 돌려드립니다.
      </p>
    </div>
  )
}
