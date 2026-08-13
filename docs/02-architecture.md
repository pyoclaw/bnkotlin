# Architecture

## Chosen stack

- Kotlin Multiplatform
- Compose Multiplatform
- Ktor
- PostgreSQL
- SQLDelight / SQLite
- WebSockets
- Kotlin Coroutines / Flow
- Kotlinx Serialization

## High-level shape

```text
Customer apps
  -> Compose Multiplatform UI
  -> Shared domain + state
  -> Ktor backend
  -> PostgreSQL
  -> Realtime events
  -> Kitchen/admin clients
```

## Rules

- Shared Kotlin owns business logic.
- UI is shared where practical.
- Backend owns canonical state.
- Clients subscribe to state changes.
- Offline mode must not break kitchen operation.

## Service boundaries

- API service
- Notification worker
- Realtime dispatcher
- Optional print adapter later
