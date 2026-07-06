# syntax=docker/dockerfile:1
# Multi-stage build for license-service (Spring MVC + JPA + PostgreSQL + Flyway + etcd)

# ───────────────────── build stage ─────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY common/common-core/ ./common/common-core/
COPY common/common-security/ ./common/common-security/
COPY common/common-etcd/ ./common/common-etcd/
COPY services/license-service/ ./services/license-service/

RUN mvn -q -pl services/license-service -am dependency:go-offline || true
RUN mvn -q -pl services/license-service -am clean package -DskipTests

# ───────────────────── runtime stage ───────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/services/license-service/target/license-service-*.jar /app/license-service.jar

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/license-service.jar"]
