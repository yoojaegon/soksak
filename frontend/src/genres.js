// 장르(태그) 단일 소스. value는 백엔드 Genre enum 이름과 반드시 일치해야 한다.
// API는 태그를 enum 이름(예: "ROMANCE")으로 주고받고, 한글 라벨은 프론트에서만 매핑한다.
export const GENRES = [
  { value: 'ROMANCE', label: '로맨스' },
  { value: 'FANTASY', label: '판타지' },
  { value: 'DAILY', label: '일상' },
  { value: 'HORROR', label: '공포' },
  { value: 'MYSTERY', label: '미스터리' },
  { value: 'COMEDY', label: '코미디' },
  { value: 'ACTION', label: '액션' },
  { value: 'HISTORICAL', label: '시대극' },
  { value: 'SCIFI', label: 'SF' },
  { value: 'MARTIAL', label: '무협' },
  { value: 'SPORTS', label: '스포츠' },
  { value: 'ETC', label: '기타' },
]

const LABEL_BY_VALUE = Object.fromEntries(GENRES.map((g) => [g.value, g.label]))

// enum 이름 → 한글 라벨. 모르는 값이면 원문을 그대로 돌려준다(백엔드가 목록을 늘렸을 때 안전).
export const genreLabel = (value) => LABEL_BY_VALUE[value] ?? value

// 태그 배열 토글: 이미 있으면 빼고, 없으면 추가한다. (캐릭터 폼의 장르 다중선택 공용)
export const toggleTag = (tags = [], value) =>
  tags.includes(value) ? tags.filter((t) => t !== value) : [...tags, value]
