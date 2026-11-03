# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Copy Gradle wrapper and build descriptors first so dependency resolution is cached.
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew \
    && ./gradlew --no-daemon dependencies

# Copy source only after dependencies are resolved.
COPY src ./src

RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/uploads \
    && chown -R app:app /app

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
