import { useState } from 'react'

// 캐릭터 이미지 한 장. 이미지가 없거나 불러오지 못하면 children(이름 첫 글자 등)으로 되돌린다.
// 주소를 제작자가 직접 넣는 외부 URL이라 404가 흔한데, 깨진 이미지 아이콘이 뜨는 것보다
// 원래의 모노그램 자리표시자를 보여주는 편이 낫다.
// 실패한 주소를 기억해 두는 이유: 목록에서 다른 캐릭터로 재사용돼도 실패 상태가 따라붙지 않게.
export default function CharacterImage({ src, children, ...imgProps }) {
  const [failedSrc, setFailedSrc] = useState(null)

  if (!src || src === failedSrc) return children

  return <img src={src} alt="" onError={() => setFailedSrc(src)} {...imgProps} />
}
