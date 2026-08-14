# Tech Stack

## Shared

- Kotlin 2.x
- Kotlin Multiplatform
- Compose Multiplatform
- Kotlinx Serialization
- Kotlin Coroutines / Flow
- SQLDelight (type-safe multiplatform local persistence)

## Backend

- Ktor server
- Exposed (server-side SQL access over JDBC)
- PostgreSQL (production source of truth)
- Flyway (versioned migrations)
- HikariCP (connection pool)
- WebSockets
- Transactional outbox for realtime event delivery

## Clients

- Android customer app (planned)
- iOS customer app (planned)
- Web customer app via Compose Multiplatform Web/Wasm where suitable
- Desktop kitchen app (planned)
- Desktop admin app (planned)

Local persistence for clients is SQLDelight over SQLite (`shared:sync`);
PostgreSQL is never accessed from a client.

## Integrations

- Payment provider abstraction
- Push notifications
- SMS provider
- Receipt printer support
