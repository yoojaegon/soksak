# 속삭 프론트엔드

React + Vite로 만든 최소 기능(MVP) 웹 프론트엔드입니다.

기능: 회원가입 / 로그인 → 캐릭터 목록 → 캐릭터와 채팅.

## 실행 방법

```bash
cd frontend
npm install      # 처음 한 번만
npm run dev      # 개발 서버 실행 → http://localhost:5173
```

백엔드(`backend`, 8080 포트)가 함께 떠 있어야 합니다.
Vite 개발 서버가 `/auth`, `/signup`, `/characters`, `/chatrooms` 요청을
자동으로 `http://localhost:8080`(백엔드)로 넘겨줍니다(프록시). 그래서 CORS 설정 없이 동작합니다.

채팅 응답(AI)까지 실제로 보려면 AI 서버(`ai-server`, 8000 포트)도 떠 있어야 합니다.
AI 서버 없이 화면만 확인하려면 백엔드의 `StubChatAiClient`를 쓰도록 설정하면 됩니다.

## 폴더 구조

```
src/
  main.jsx            앱 진입점
  App.jsx             라우팅 + 상단바
  auth.jsx            로그인 상태 공유(Context)
  api.js              백엔드 API 호출 + 토큰 관리
  styles.css          전체 스타일
  pages/
    LoginPage.jsx
    SignupPage.jsx
    CharactersPage.jsx
    ChatPage.jsx
```

## 빌드

```bash
npm run build        # dist/ 에 정적 파일 생성
npm run preview      # 빌드 결과 미리보기
```
