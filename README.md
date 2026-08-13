# Brød & Fyld Next

Kotlin-first restaurant operating platform for pickup ordering, realtime
kitchen operations, and future delivery expansion. Built as a reusable
RestaurantOS with Brød & Fyld (a Danish sandwich shop) as the first deployment.

## Stack

- Kotlin Multiplatform + Compose Multiplatform (shared UI)
- Ktor (API, auth, WebSocket realtime)
- PostgreSQL (canonical persistence)
- SQLDelight / SQLite (offline kitchen cache, outbox)
- Kotlin Coroutines / Flow, kotlinx.serialization

See `docs/` for the full product, architecture and domain specifications and
`PLAN.md` for the implementation plan, toolchain decisions and open questions.

## Repository layout

```text
shared/
  domain/          models, order state machine, pricing, payments (common code)
  ui/              Compose Multiplatform design system and screens
  sync/            (planned) realtime client + offline outbox
  networking/      (planned) Ktor client wrapper
  payments/        (planned) payment provider abstraction
backend/
  ktor-api/        Ktor server (HTTP + WebSocket)
  worker/          (planned) background worker
apps/
  customer/        (planned) Android + iOS + web
  kitchen/         (planned) desktop kitchen app
  admin/           (planned) desktop admin app
database/          (planned) migrations
deploy/            (planned) deployment config
docs/              product and architecture specs
```

## Requirements

- JDK 21+ (Temurin recommended)
- Android Studio (KMP), Xcode (iOS), Docker (PostgreSQL) as targets are added

## Build & test

```bash
./gradlew build          # compile + run all tests (JVM targets)
./gradlew test           # run tests only
./gradlew :backend:ktor-api:run   # start the API (default port 8080)
```

## Running the API

```bash
SERVER_PORT=8080 ./gradlew :backend:ktor-api:run
```

Endpoints:

- `GET /health/live`, `GET /health/ready`
- `GET /v1/restaurant/status`
- `GET /v1/menu`
- `WS /v1/ws/orders`

## Configuration

Server configuration is environment-driven (see `docs/21-configuration.md`).
Core variables: `SERVER_PORT`, `DATABASE_URL`, `JWT_SECRET`, `JWT_ISSUER`,
`JWT_AUDIENCE`, `PAYMENT_PROVIDER`, `PAYMENT_SECRET`,
`PAYMENT_WEBHOOK_SECRET`, `SMS_PROVIDER`, `PUSH_PROVIDER`, `LOG_LEVEL`.

Never commit secrets; `.env*` is gitignored.

## Working rules

- Backend is the source of truth.
- Shared Kotlin owns business logic.
- Compose owns UI.
- Realtime first, offline-tolerant kitchen.
- Payment providers stay behind an interface.
