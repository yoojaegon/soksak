## What this project is

**soksak (속삭)** is a **character-chat website** — users hold conversations with AI characters. The chat experience is powered by a **LangChain-based chat pipeline** that already exists separately and will be brought into this repo later (expected to live in `ai-server/`). Do not rebuild the chat pipeline from scratch; it is being imported.

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

## Workflow

- **Do not run `git commit`.** The user makes all commits themselves. You may stage changes and suggest a commit message, but never create the commit.