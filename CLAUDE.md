## What this project is

**soksak (속삭)** is a **character-chat website** — users hold conversations with AI characters. The chat experience is powered by a **Python LangChain chat pipeline** that already exists separately and will be brought into this repo (in `ai-server/`) **as-is** — do not rebuild it from scratch. The Java backend is now in place, so the integration phase has begun: `ai-server/` **may be modified when integration requires it**. Still treat the imported pipeline as the baseline — don't rebuild it from scratch; change it only when a concrete need calls for it.

The intended split: `backend/` handles the web/application layer (users, characters, persistence, REST API) while the AI service handles the LLM chat pipeline.

```bash
cd backend
./gradlew bootRun          # run the app
./gradlew build            # compile + run tests + package
./gradlew test             # run all tests
./gradlew test --tests "com.soksak.soksak.SoksakApplicationTests"   # single test class
./gradlew test --tests "*.SoksakApplicationTests.contextLoads"      # single test method
```

## Backend stack

- Spring Boot 3.5.14, Java 17 (toolchain-pinned).
- Spring Web (REST), Spring Data JPA, PostgreSQL driver (runtime).
- Lombok — annotation processing is configured; use it for boilerplate (getters, builders, etc.).

## Schema (Flyway)

- The schema is owned by Flyway (`backend/src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`, which checks tables, columns and types only — not lengths, indexes or constraints. A mismatch there passes startup silently.
- Every schema change is a new `V{n}__description.sql`. Never edit a migration that has already been applied anywhere; Flyway rejects startup on checksum mismatch.
- **Enum columns carry CHECK constraints** listing the allowed values (`character_tags`, `chat_room.thinking_level`, `message.role`, `reports.reason/target`, `users.gender`). Adding an enum value requires a migration that drops and re-adds that CHECK, or inserts fail with 23514. Removing a value means cleaning up rows that use it first. `credit_ledger.reason` is the exception: plain `varchar(20)` with no CHECK.
- Tests run on Postgres via Testcontainers (`TestcontainersConfig`, imported by each `@SpringBootTest`) with the same migrations, so host `./gradlew test` needs Docker.

Base package for new code: `com.soksak.soksak`.

Secrets note: DB password and JWT `secret-key` now live in the root `.env` (read by both docker-compose and the backend via `spring-dotenv`) — see "Running locally" below. `application.yml` references them via `${...}` placeholders; don't hard-code secrets back into it.

## Running (one compose file for local and deploy)

`docker-compose.yml` is the single source for both. The only differences are the profile and the `.env` values.

Prerequisite (once): copy `.env.example` to `.env` at the repo root and fill it in. Compose reads this one file and injects the values into every container; `spring-dotenv` reads the same file when the backend is run outside a container.

```bash
# Local — Postgres container included → http://localhost
docker compose --profile local up -d --build

# Deploy (EC2) — DB is RDS, so Postgres is not started
docker compose up -d --build
```

Notes:
- **All API paths live under `/api`** (`server.servlet.context-path`), because SPA routes and API paths collide (`/characters/3/edit` is a screen, `/characters/3` is an API). Caddy (`frontend/Caddyfile`) routes `/api/*` to the backend and everything else to `index.html`; the frontend prepends the same prefix in `api.js` (`API_BASE`).
- Only `web` (ports 80, 443) is exposed. backend/ai-server are reachable only inside the compose network, by service name (`http://ai-server:8000`, not localhost).
- Uploaded images live in the `uploads` volume (`UPLOADS_DIR=/data/uploads` in the container). Without that volume they vanish whenever the container is recreated.
- Frontend hot reload is gone with this setup — `npm run dev` (Vite, :5173, proxies `/api` → :8080) still works if the backend is up.

## Workflow

- **Do not write or edit code unless the user explicitly asks for it.** By default, act as an advisor: answer questions, explain trade-offs, suggest approaches, and review. Only create or modify code files when the user clearly requests an implementation (e.g. "make it", "write it", "fix it", "apply it"). When in doubt, give advice and ask whether they want you to implement it. (Reading/searching the codebase to inform advice is always fine.)
- **Do not run `git commit`.** The user makes all commits themselves. You may stage changes and suggest a commit message, but never create the commit.