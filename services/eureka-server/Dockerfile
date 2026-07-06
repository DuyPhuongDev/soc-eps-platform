# syntax=docker/dockerfile:1
# Multi-stage build for Eureka Server (Spring Cloud Netflix Eureka)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full repository so Maven can resolve the full reactor for this build.
COPY . ./

# Pre-fetch dependencies (cached layer unless POM changes).
RUN mvn -q -pl services/eureka-server -am dependency:go-offline -DincludeScope=test || true

# Package the fat JAR.
RUN mvn -q -pl services/eureka-server -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/eureka-server/target/eureka-server-*.jar /app/eureka-server.jar

USER app

EXPOSE 8761

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/eureka-server.jar"]
