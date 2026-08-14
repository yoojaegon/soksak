# 1) 빌드 — gradle wrapper로 실행 가능한 jar를 만든다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성만 먼저 받아 레이어로 굳힌다. 소스만 고쳤을 때 재빌드가 짧아진다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# 테스트는 DB가 있어야 돌아서 이미지 빌드에서는 건너뛴다(로컬에서 ./gradlew test로 돌릴 것).
RUN ./gradlew --no-daemon bootJar -x test

# 2) 실행 — JRE만 있는 작은 이미지로 옮긴다.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# 컨테이너에 걸린 메모리 한도를 기준으로 힙을 잡는다.
# 프리티어(1GB)에서는 이걸 안 주면 JVM이 호스트 전체 메모리를 보고 힙을 크게 잡아 OOM으로 죽는다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]