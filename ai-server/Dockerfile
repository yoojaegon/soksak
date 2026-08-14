FROM python:3.12-slim

# uv는 공식 이미지에서 실행 파일만 복사해 쓴다(별도 설치 단계 없이 가장 빠름).
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app
ENV UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv

# 의존성을 먼저 설치해 레이어로 굳힌다(코드만 바뀌면 이 단계는 재사용).
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY app app

# 가상환경을 PATH에 올려 uvicorn을 그대로 실행한다.
ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]