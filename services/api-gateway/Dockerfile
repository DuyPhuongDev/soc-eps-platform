# syntax=docker/dockerfile:1
# Multi-stage build for api-gateway (Spring Cloud Gateway WebFlux)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common/common-core/ ./common/common-core/
COPY common/common-security/ ./common/common-security/
COPY services/api-gateway/ ./services/api-gateway/

RUN mvn -q -pl services/api-gateway -am dependency:go-offline || true
RUN mvn -q -pl services/api-gateway -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/api-gateway/target/api-gateway-*.jar /app/api-gateway.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/api-gateway.jar"]
