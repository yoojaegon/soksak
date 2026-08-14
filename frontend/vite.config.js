import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 서버(5173) → 백엔드(8080)로 API 요청을 프록시한다.
// 이렇게 하면 브라우저 입장에선 같은 출처라 CORS 문제가 생기지 않는다.
// (백엔드에 별도 CORS 설정이 없어도 됨)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // API는 전부 /api 아래라 프록시 규칙도 한 줄이면 된다.
    // (업로드된 이미지 주소도 /api/uploads/... 로 내려오므로 여기에 함께 걸린다.)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
