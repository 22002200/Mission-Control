# syntax=docker/dockerfile:1

# =============================================================================
# dev - what `docker compose up` runs.
#
# Runs the app straight from source via the Maven wrapper. `compose.yaml` uses
# `develop.watch` with sync+restart, so a source change restarts this container
# and Maven recompiles. The image deliberately contains no application code:
# Compose syncs it in.
# =============================================================================
FROM maven:3.9.16-eclipse-temurin-21 AS dev
WORKDIR /app

# Warm the dependency cache as its own layer so it survives source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q dependency:go-offline

COPY src/ src/
EXPOSE 8080
CMD ["./mvnw", "-B", "spring-boot:run"]

# =============================================================================
# build - produces the executable jar.
# =============================================================================
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package && \
    cp target/*.jar /app/application.jar

# =============================================================================
# runtime - slim JRE image used by compose.prod.yaml.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run the application as root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build --chown=spring:spring /app/application.jar application.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
