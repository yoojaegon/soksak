# 1) 빌드 — Vite 정적 파일(dist) 생성
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 2) 실행 — nginx가 정적 파일을 서빙하고 /api 요청만 백엔드로 넘긴다.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
