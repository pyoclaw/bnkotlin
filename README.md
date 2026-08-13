# Brød & Fyld Next

Kotlin-first restaurant operating platform for pickup ordering, realtime
kitchen operations, and future delivery expansion. Built as a reusable
RestaurantOS with Brød & Fyld (a Danish sandwich shop) as the first deployment.

## Stack

- Kotlin Multiplatform + Compose Multiplatform (shared UI)
- Ktor (API, auth, WebSocket realtime)
- Exposed + PostgreSQL (server-side persistence, source of truth)
- SQLDelight / SQLite (client cache, offline state, outbox)
- Kotlin Coroutines / Flow, kotlinx.serialization

See `docs/` for the full product, architecture and domain specifications and
`PLAN.md` for the implementation plan, toolchain decisions and open questions.

## Repository layout

```text
shared/
  domain/          models, order state machine, pricing, payments (common code)
  ui/              Compose Multiplatform design system and screens
  sync/            SQLDelight/SQLite client cache + mutation outbox
  networking/      (planned) Ktor client wrapper
  payments/        (planned) payment provider abstraction
backend/
  ktor-api/        Ktor server (HTTP + WebSocket)
  worker/          (planned) background worker
apps/
  customer/        (planned) Android + iOS + web
  kitchen/         (planned) desktop kitchen app
  admin/           (planned) desktop admin app
database/          PostgreSQL migrations (Flyway)
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

Start a local PostgreSQL first:

```bash
docker compose up -d postgres
```

Then run the API against it (development defaults match the compose file):

```bash
DATABASE_URL='jdbc:postgresql://localhost:5432/brodogfyld?user=brodogfyld&password=brodogfyld' \
  SERVER_PORT=8080 ./gradlew :backend:ktor-api:run
```

Without `DATABASE_URL` the API falls back to an in-memory order repository
(development only).

Endpoints:

- `GET /health/live`, `GET /health/ready`
- `GET /v1/restaurant/status`
- `GET /v1/menu`
- `WS /v1/ws/orders` — realtime order event stream (`?restaurantId=` scopes it)

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
