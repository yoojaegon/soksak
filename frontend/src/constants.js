// 앱 전역에서 공유하는 상수.

// 성별 코드 ↔ 한글 라벨 (백엔드 Gender enum과 1:1). 회원가입에서만 쓴다 —
// 페르소나/캐릭터는 성별을 따로 받지 않고 자유 서술 본문에 쓴다.
export const GENDERS = [
  { value: 'MALE', label: '남성' },
  { value: 'FEMALE', label: '여성' },
  { value: 'OTHER', label: '기타' },
]
