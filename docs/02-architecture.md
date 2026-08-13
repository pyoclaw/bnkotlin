# Architecture

## Chosen stack

- Kotlin Multiplatform
- Compose Multiplatform
- Ktor
- Exposed (server-side SQL access)
- PostgreSQL (production source of truth)
- SQLDelight / SQLite (client local persistence)
- WebSockets
- Kotlin Coroutines / Flow
- Kotlinx Serialization
- Flyway (versioned migrations)

## Server layering

```text
Ktor route
  -> Application service (OrderService)
  -> Repository interface (OrderRepository)
  -> Exposed
  -> PostgreSQL
```

- The domain never depends on Exposed or JDBC.
- Compose clients never talk to PostgreSQL directly; they reach it only via the
  Ktor API over HTTPS/WebSocket.
- SQL queries live in Exposed repositories, never in route handlers.

## Client layering

```text
Compose UI
  -> ViewModel / presentation state
  -> Shared repository
  -> SQLDelight
  -> SQLite (cache, offline state, mutation outbox)
```

## Ownership

- **PostgreSQL owns** durable business state: restaurants, menu, orders,
  order_items, payments, order_events, outbox, audit timeline.
- **SQLite owns** cached state, offline state, local UI state, sync cursor and
  the pending-mutation outbox.
- **Ktor owns** orchestration, authorization, validation, state transitions and
  realtime coordination.
- **Compose owns** presentation, interaction and local UI state.

## Rules

- Shared Kotlin owns business logic.
- Backend is the authoritative source of truth.
- Clients subscribe to state changes; local caches are optimistic but never
  authoritative.
- Order mutations are optimistic-concurrency protected (order.version) and
  transactional, with a durable outbox event per committed change.
- Offline mode must not break kitchen operation.
