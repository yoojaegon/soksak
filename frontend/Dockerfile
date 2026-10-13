# 1) 빌드 — Vite 정적 파일(dist) 생성
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 2) 실행 — Caddy가 정적 파일을 서빙하고 /api 요청만 백엔드로 넘긴다.
# 배포에서는 HTTPS도 Caddy가 맡는다.
FROM caddy:2.10.2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
EXPOSE 80 443
