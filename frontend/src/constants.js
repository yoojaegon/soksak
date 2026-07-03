// 앱 전역에서 공유하는 상수.

// 성별 코드 ↔ 한글 라벨 (백엔드 Gender enum과 1:1)
export const GENDERS = [
  { value: 'MALE', label: '남성' },
  { value: 'FEMALE', label: '여성' },
  { value: 'OTHER', label: '기타' },
]

// 성별 코드를 한글 라벨로. 모르는 값이면 코드를 그대로 돌려준다.
export const genderLabel = (g) => GENDERS.find((x) => x.value === g)?.label ?? g
