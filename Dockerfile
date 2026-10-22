# syntax=docker/dockerfile:1

# Books Manager API image (RNF26): two stages, so the final image has only the JRE and the jar.
# Build: docker build -t booksmanager .   Run: see "Executar em contêiner" in the README.

# --- Build: Temurin 25 JDK ------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# The dependencies first, in their own layer: they change far less often than the source, so a change in
# src/ does not download them again. The Maven repository is also kept in a build cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

# The tests run in the CI before the image is built, so they are not repeated here.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -DskipTests -Djacoco.skip=true package \
    && mv target/BooksManager-*.jar app.jar

# --- Run: Temurin 25 JRE ----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.title="Books Manager API" \
      org.opencontainers.image.source="https://github.com/Riverfount/BooksManager"

# curl, for the health check: the JRE image does not bring it. Installed as root, before dropping to the user.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# A user with no privileges: the application never needs to be root.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

# The configuration comes only from environment variables (nothing secret is in the image). The image runs the
# production profile (application-prod.properties). The Docker Compose support of Spring Boot is for development,
# so it is off here; the database is given by SPRING_DATASOURCE_*.
ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_DOCKER_COMPOSE_ENABLED=false \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Healthy when the application answers UP, database included (the endpoint answers 503 otherwise).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:8080/actuator/health || exit 1

# exec: the JVM is the main process, so it receives the stop signal and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
