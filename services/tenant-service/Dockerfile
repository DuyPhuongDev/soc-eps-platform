# syntax=docker/dockerfile:1
# Multi-stage build for tenant-service (Spring MVC + JPA + PostgreSQL + Flyway)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full repository so Maven can resolve the full reactor for this build.
COPY . ./

# Pre-fetch dependencies.
RUN mvn -q -pl services/tenant-service -am dependency:go-offline -DincludeScope=test || true

# Package the fat JAR (includes all common modules).
RUN mvn -q -pl services/tenant-service -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/tenant-service/target/tenant-service-*.jar /app/tenant-service.jar

USER app

EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/tenant-service.jar"]
