# syntax=docker/dockerfile:1
# Multi-stage build for the collector service (Spring Boot 3.5 / Java 21 WebFlux).
# No local Maven build required — the build stage compiles from source inside the image.

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full repository so Maven can resolve the full reactor for this build.
COPY . ./

RUN mvn -q -pl ingestion/collector -am dependency:go-offline -DincludeScope=test || true
RUN mvn -q -pl ingestion/collector -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S app && adduser -S app -G app

# Copy the Spring Boot fat JAR. spring-boot-maven-plugin produces
# collector-<version>.jar (the executable fat JAR) — use a glob to avoid
# hardcoding the version, while excluding *-sources/*-javadoc/*.original.
COPY --from=build /build/ingestion/collector/target/collector-*.jar /app/collector.jar

USER app

EXPOSE 8081

# Reactive WebFlux server; cap heap at 75% of container memory for k8s/compose limits.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/collector.jar"]
