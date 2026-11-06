// 입력칸 아래에 "지금 글자 수 / 한도"를 보여준다.
// 한도의 90%를 넘으면 색을 바꿔 곧 찬다는 걸 알린다. 넘치는 입력은 maxLength가 막으므로 경고까지는 하지 않는다.
// showFrom을 주면 그 글자 수부터만 보인다. 늘 보이면 시끄러운 채팅 입력창 같은 곳에 쓴다.
export default function CharCount({ value, max, showFrom = 0, className = '' }) {
  const length = value?.length ?? 0
  if (length < showFrom) return null
  const near = length >= max * 0.9
  return (
    <span className={`char-count${near ? ' near' : ''}${className ? ` ${className}` : ''}`}>
      {length} / {max}
    </span>
  )
}
