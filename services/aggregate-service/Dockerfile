# syntax=docker/dockerfile:1
# Multi-stage build for aggregate-service (Spring MVC + JPA + Kafka + Redis + TimescaleDB)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full repository so Maven can resolve the full reactor for this build.
COPY . ./

RUN mvn -q -pl services/aggregate-service -am dependency:go-offline -DincludeScope=test || true
RUN mvn -q -pl services/aggregate-service -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/aggregate-service/target/aggregate-service-*.jar /app/aggregate-service.jar

USER app

EXPOSE 8084

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/aggregate-service.jar"]
