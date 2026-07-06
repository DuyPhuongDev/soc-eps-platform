# syntax=docker/dockerfile:1
# Multi-stage build for notification-service (Spring MVC + JPA + Kafka + SMTP)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common/common-core/ ./common/common-core/
COPY common/common-security/ ./common/common-security/
COPY common/common-kafka/ ./common/common-kafka/
COPY services/notification-service/ ./services/notification-service/

RUN mvn -q -pl services/notification-service -am dependency:go-offline || true
RUN mvn -q -pl services/notification-service -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/notification-service/target/notification-service-*.jar /app/notification-service.jar

USER app

EXPOSE 8085

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/notification-service.jar"]
