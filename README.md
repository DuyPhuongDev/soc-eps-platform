# SOC EPS Platform

A production-ready, multi-tenant license enforcement platform with clear **Control Plane / Data Plane** separation.
Manages tenant onboarding, license lifecycle, real-time event ingestion with enforcement (token bucket + EPS metering),
and usage metrics aggregation.

## Architecture

<img src="/images/architect.png" alt="architect">

### Key Design Decisions

- **Control Plane / Data Plane separation** — services that manage state (tenant, license) are isolated from the
  ingestion hot path (collector). The collector has zero dependency on PostgreSQL or JPA.
- **etcd for policy distribution** — license changes are published to etcd and pushed to the collector via watch,
  eliminating HTTP polling between services in the hot path.
- **Kafka for usage events** — collector forwards validated events to Kafka; the metric aggregator consumes them
  asynchronously for aggregation into TimescaleDB.
- **Auth stays in tenant-service** — no separate auth microservice. User management and authentication are co-located
  with tenant management since they share the same bounded context.
- **No shared entities** — services own their database schemas and Flyway migrations. Only DTOs and enums are shared via
  `common-model`.

## Tech Stack

| Layer                    | Technology                            |
|--------------------------|---------------------------------------|
| Runtime                  | Java 21                               |
| Framework                | Spring Boot 3.5.0                     |
| Build                    | Maven (multi-module)                  |
| Database (Control Plane) | PostgreSQL 17                         |
| Database (Metrics)       | TimescaleDB (PostgreSQL extension)    |
| Cache & Rate Limiting    | Redis 7                               |
| Messaging                | Apache Kafka 7.7.0 (KRaft mode)       |
| Policy Distribution      | etcd 3.5                              |
| Collector Stack          | Spring WebFlux (reactive)             |
| Control Plane Stack      | Spring Web MVC + Spring Data JPA      |
| Migration                | Flyway                                |
| API Documentation        | springdoc-openapi 2.8.9               |
| Authentication           | JWT (jjwt 0.12.6)                     |
| Containerization         | Docker Compose (dev), K8s/Helm (prod) |

## Project Structure

```
eps-license-platform/
├── common/                          # Shared libraries (thin, optional, layered)
│   ├── common-core/                 # Pure Java utilities, exceptions — zero Spring deps
│   ├── common-model/                # Cross-service DTOs & enums — only Lombok
│   ├── common-security/             # JWT verification, auth filter (Spring Security)
│   ├── common-kafka/                # Kafka producer/consumer config + serialization
│   ├── common-redis/                # Reactive Redis config (ReactiveRedisTemplate)
│   └── common-etcd/                 # etcd client + watch wrapper with retry
│
├── services/                        # Control Plane
│   ├── tenant-service/              # Tenant CRUD, user management, auth (JWT login)
│   ├── license-service/             # License CRUD, policy publishing to etcd
│   ├── dashboard-service/           # NEW: read-only metrics & reports (skeleton)
│   └── metric-aggregator/           # NEW: Kafka consumer → aggregation → TimescaleDB (skeleton)
│
├── ingestion/                       # Data Plane
│   └── collector-service/           # Reactive event ingestion, enforcement, Kafka forwarding
│
├── docs/                            # Architecture specs and ADRs
│   └── SPEC-architecture.md         # Full architecture specification
│
├── infrastructure/                  # Dev infrastructure configs
├── deployment/                      # Dockerfiles, K8s manifests, Helm charts
└── docker-compose.yaml              # Local development stack
```

## Prerequisites

- **Java 21** (e.g., `sdk use java 21.0.6-tem`)
- **Docker & Docker Compose** (for infrastructure: PostgreSQL, Redis, Kafka, etcd)
- **Maven** (the project includes `mvnw` wrapper if Maven is not installed)

### Infrastructure (via Docker Compose)

```bash
# Start PostgreSQL, Redis, Kafka, etcd
docker compose up -d
```

| Service    | Port | Notes                                  |
|------------|------|----------------------------------------|
| PostgreSQL | 5434 | Database: `eps_db`, User: `eps_user`   |
| Redis      | 6379 | No password (dev)                      |
| Kafka      | 9092 | KRaft mode, auto-create topics enabled |
| etcd       | 2379 | Policy distribution                    |

## Quick Start

```bash
# 1. Clone and start infrastructure
git clone <repo-url>
cd eps-license-platform
docker compose up -d

# 2. Build all modules
./mvnw clean install

# 3. Run services (each in its own terminal, or use your IDE)

# Tenant Service (port 8082)
./mvnw -pl services/tenant-service spring-boot:run

# License Service (port 8083)
./mvnw -pl services/license-service spring-boot:run

# Collector Service (port 8081)
./mvnw -pl ingestion/collector spring-boot:run
```

Each service can also be built and run independently:

```bash
./mvnw clean install -pl services/tenant-service -am
cd services/tenant-service && ./mvnw spring-boot:run
```

## Services

| Service               | Port | Type          | Stack                     | Database                 |
|-----------------------|------|---------------|---------------------------|--------------------------|
| **tenant-service**    | 8082 | Control Plane | Spring MVC + JPA          | PostgreSQL               |
| **license-service**   | 8083 | Control Plane | Spring MVC + JPA          | PostgreSQL               |
| **collector-service** | 8081 | Data Plane    | Spring WebFlux (reactive) | None (Redis + etcd only) |
| **dashboard-service** | 8084 | Control Plane | Spring MVC + JPA          | PostgreSQL (read)        |
| **metric-aggregator** | 8085 | Data Plane    | Spring MVC + JPA + Kafka  | TimescaleDB              |

### API Endpoints

All endpoints are prefixed with `/api/v1/`. Full API documentation is available via Swagger UI when each service is
running:

| Service           | Swagger UI                            |
|-------------------|---------------------------------------|
| tenant-service    | http://localhost:8082/swagger-ui.html |
| license-service   | http://localhost:8083/swagger-ui.html |
| collector-service | http://localhost:8081/swagger-ui.html |

#### Tenant Service (`tenant-service`)

| Method | Endpoint                    | Description                              |
|--------|-----------------------------|------------------------------------------|
| POST   | `/api/v1/auth/login`        | Authenticate user, get JWT               |
| POST   | `/api/v1/tenants`           | Create tenant                            |
| GET    | `/api/v1/tenants`           | List all tenants                         |
| GET    | `/api/v1/tenants/{id}`      | Get tenant by ID                         |
| PUT    | `/api/v1/tenants/{id}`      | Update tenant                            |
| DELETE | `/api/v1/tenants/{id}`      | Delete tenant                            |
| GET    | `/api/v1/internal/api-keys` | Internal: API key cache sync (collector) |

#### License Service (`license-service`)

| Method | Endpoint                           | Description             |
|--------|------------------------------------|-------------------------|
| POST   | `/api/v1/licenses`                 | Create license          |
| GET    | `/api/v1/licenses`                 | List all licenses       |
| GET    | `/api/v1/licenses/{id}`            | Get license by ID       |
| PUT    | `/api/v1/licenses/{id}`            | Update license          |
| DELETE | `/api/v1/licenses/{id}`            | Revoke license          |
| GET    | `/api/v1/licenses/{id}/audit-logs` | Get license audit trail |

#### Collector Service (`collector-service`)

| Method | Endpoint         | Description                       |
|--------|------------------|-----------------------------------|
| POST   | `/api/v1/events` | Ingest usage event (API key auth) |

## Data Flow

### Policy Distribution (etcd Watch)

```
license-service ──[CRUD]──► etcd ──[watch]──► collector-service (PolicyCache)
```

No HTTP polling between services. License changes propagate near-real-time via etcd watch.

### Usage Event Flow (Kafka)

```
client ──[POST /api/v1/events]──► collector ──[validate + enforce]──► Kafka ──► metric-aggregator ──► TimescaleDB
```

### API Key Refresh (HTTP, periodic)

```
collector-service ──[GET /internal/api-keys, every 30s]──► tenant-service
```

API key distribution remains HTTP-based since keys change infrequently.

## Development

### Build & Test

```bash
# Build all modules
./mvnw clean install

# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl services/tenant-service

# Verify collector has no JPA dependency (critical boundary check)
./mvnw dependency:tree -pl ingestion/collector | grep -i jpa  # should return nothing
```

### Code Style

- Package structure: `com.vdt.soc.{domain}.{layer}` (e.g., `com.vdt.soc.tenant.controller`)
- DTOs: records where possible, `@Data` classes for mutable request bodies
- Services: interface + implementation pair
- Controllers: thin — delegate to service layer
- Exception handling: per-service `GlobalExceptionHandler` using `@RestControllerAdvice`

### Dependency Rules

| Module              | May depend on                                                                | Must NOT depend on                      |
|---------------------|------------------------------------------------------------------------------|-----------------------------------------|
| `common-core`       | Nothing (pure Java)                                                          | Spring Boot, JPA, Redis, Kafka, etcd    |
| `common-model`      | `common-core`                                                                | Spring Boot, JPA                        |
| `common-security`   | `common-core`, `common-model`                                                | JPA, Redis, Kafka                       |
| `collector-service` | `common-core`, `common-model`, `common-redis`, `common-kafka`, `common-etcd` | **JPA, PostgreSQL, any service module** |

See [SPEC-architecture.md](docs/SPEC-architecture.md) for the complete dependency matrix.

### Adding a New Feature

1. Identify which bounded context the feature belongs to
2. If it spans services, define the contract (DTO) in `common-model`
3. For new infrastructure concerns, consider a new `common-*` module (ask first)
4. Follow the [boundaries](#boundaries) below

## Boundaries

### Always

- `./mvnw test` passes before committing
- `common-*` modules stay free from service-module dependencies
- Each service owns its database schema and Flyway migrations
- Use `common-model` DTOs for cross-service contracts — never share `@Entity` classes
- Collector must never depend on JPA or PostgreSQL
- API endpoints under `/api/v1/{resource}`
- Use `@Transactional(readOnly = true)` for query methods

### Never

- Add JPA/PostgreSQL dependency to `collector-service`
- Make `collector-service` depend on `license-service` or `tenant-service` JARs
- Share entity classes across services
- Call `license-service` HTTP API from `collector-service` (use etcd watch)
- Add business logic to `common-*` modules
- Create circular dependencies between modules
- Commit secrets or credentials in `application.yaml`

## Deployment

Production deployment uses Kubernetes with Helm charts. See `deployment/` for:

- **Dockerfiles** — `deployment/docker/Dockerfile.{service-name}` for each service
- **Kubernetes** — `deployment/k8s/` with base configs and environment overlays
- **Helm** — `deployment/helm/eps-license-platform/` for templated deployments

## Documentation

- [Architecture Specification](docs/SPEC-architecture.md) — full architecture spec, module boundaries, migration plan
- [Collector Service Spec](docs/specs/collector-service-spec.md) — detailed collector design

## License

Proprietary — all rights reserved.

---

**Maintainer:** Tran Duy Phuong ([@dphuongdev](https://github.com/dphuongdev))
