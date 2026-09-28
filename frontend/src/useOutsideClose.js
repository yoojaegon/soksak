import { useEffect } from 'react'

// 열려 있는 팝업 메뉴를 바깥 클릭으로 닫는다.
// - 캡처 단계에서 듣는다: 어느 버튼이 버블링을 끊어도 닫기가 새지 않도록.
// - 그래서 메뉴 쪽 버튼은 stopPropagation을 하지 않는다(끊으면 다른 메뉴의 바깥 클릭 판정이 안 닿아
//   두 메뉴가 동시에 열린 채 남는다). 안쪽 클릭인지는 contains로 가린다.
// - ref는 하나 또는 배열. 트리거 버튼도 '안쪽'에 넣어야 한다 — 빠지면 캡처에서 먼저 닫힌 뒤
//   버튼의 토글이 다시 열어 버린다.
export function useOutsideClose(ref, open, onClose) {
  useEffect(() => {
    if (!open) return
    const refs = Array.isArray(ref) ? ref : [ref]
    const close = (e) => {
      if (refs.some((r) => r.current?.contains(e.target))) return
      onClose()
    }
    document.addEventListener('click', close, true)
    return () => document.removeEventListener('click', close, true)
    // onClose는 매 렌더 새 함수라 의존성에서 뺀다 — 넣으면 렌더마다 리스너를 다시 단다.
  }, [open])
}
