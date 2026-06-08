## What this project is

**soksak (속삭)** is a **character-chat website** — users hold conversations with AI characters. The chat experience is powered by a **Python LangChain chat pipeline** that already exists separately and will be brought into this repo (in `ai-server/`) **as-is** — do not rebuild it from scratch. Any modifications to it are deferred until the Java backend is fully implemented; until then, leave `ai-server/` alone.

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

Note: a PostgreSQL datasource is on the classpath but **not configured** in `application.properties`. `bootRun` and the `contextLoads` test will fail until DB connection properties (`spring.datasource.*`) are added or a JPA/datasource config is otherwise supplied.

Base package for new code: `com.soksak.soksak`.

Secrets note: config lives in `application.yml`, which already holds values that shouldn't normally be committed (e.g. the DB password). For now, put the JWT `secret-key`/`issuer` directly in `application.yml` too — do **not** bother wiring up env-var injection yet. Moving these secrets out (env vars / `.env`) is a deferred cleanup task to do later.

## Workflow

- **Do not write or edit code unless the user explicitly asks for it.** By default, act as an advisor: answer questions, explain trade-offs, suggest approaches, and review. Only create or modify code files when the user clearly requests an implementation (e.g. "make it", "write it", "fix it", "apply it"). When in doubt, give advice and ask whether they want you to implement it. (Reading/searching the codebase to inform advice is always fine.)
- **Do not run `git commit`.** The user makes all commits themselves. You may stage changes and suggest a commit message, but never create the commit.