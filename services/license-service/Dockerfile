# syntax=docker/dockerfile:1
# Multi-stage build for license-service (Spring MVC + JPA + PostgreSQL + Flyway + etcd)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full repository so Maven can resolve the full reactor for this build.
COPY . ./

# Pre-fetch dependencies, including test scope for modules that need it during build.
RUN mvn -q -pl services/license-service -am dependency:go-offline -DincludeScope=test || true

# Package the fat JAR (includes all common modules).
RUN mvn -q -pl services/license-service -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/license-service/target/license-service-*.jar /app/license-service.jar

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/license-service.jar"]
