import { useEffect, useState } from 'react'

// 이 폭 이하에선 사이드바가 본문 위에 겹쳐 뜨는 드로어가 되고, 계정 메뉴도 상단바에서 드로어 아래로 내려간다.
// styles.css의 @media (max-width: 768px) 값과 같아야 한다.
export const DRAWER_QUERY = '(max-width: 768px)'

export function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const mql = window.matchMedia(query)
    const onChange = () => setMatches(mql.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [query])
  return matches
}
