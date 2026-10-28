// 캐릭터 목록 정렬 옵션. 카탈로그와 내 캐릭터가 같이 쓴다.
// 서버도 이 세 필드만 받는다(CharacterService.SORTABLE). 하나를 늘리면 양쪽을 같이 고칠 것.
export const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '최신순' },
  { value: 'likeCount,desc', label: '인기순' },
  { value: 'chatCount,desc', label: '대화 많은순' },
]
