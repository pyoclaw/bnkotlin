# Implementation Plan

Build in vertical slices rather than completing entire layers independently.

## Slice 1 — Foundation
- [x] Gradle multiplatform structure
- [x] Shared domain module
- [x] Design-system skeleton (Compose desktop smoke app)
- [x] Ktor server
- [x] PostgreSQL connection (Flyway migrations + Exposed repository over HikariCP)
- [x] CI build

## Slice 2 — Menu
- [x] Shared models
- [x] Schema (migration tables for categories/products/modifiers)
- [x] API (sample menu served from `/v1/menu`)
- [ ] Customer menu UI
- [x] Tests (pricing/validation unit tests)

## Slice 3 — Order
- [x] Cart (models + server-side pricing)
- [x] Order state machine (central `OrderStateMachine` + tests)
- [x] Persistence (Flyway migrations + Exposed repository with optimistic concurrency and transactional outbox; integration-tested in CI against PostgreSQL)
- [x] API (order creation, submit, fake pay, status, timeline, kitchen actions)
- [ ] Checkout UI

## Slice 4 — Payment
- [x] PaymentProvider interface
- [x] Fake/test provider (dev only)
- [ ] Provider adapter (real provider)
- [ ] Signed webhook
- [ ] Idempotency
- [ ] Payment tests

## Slice 5 — Kitchen
- [x] Kitchen API (accept/reject/delay/ready/complete endpoints)
- [ ] Kitchen UI
- [x] Realtime stream (order events broadcast over `/v1/ws/orders`, restaurant-scoped)
- [x] State actions (via shared state machine)
- [x] Audit timeline (timeline events on every transition)

## Slice 6 — Offline
- [x] Local cache + client outbox schema (SQLDelight/SQLite in `shared:sync`)
- [x] Offline mutation replay (reconnect + idempotent retry, FIFO via `local_outbox.seq`)
- [x] Event recovery (dedupe by version, gap-triggered resync, full kitchen recovery)
- [x] Conflict tests (stale drop + reconcile, superseded mutations, idempotent retry)
- [x] Server idempotency (mutation id doubles as the timeline event id; replayed mutations are no-ops)
- [x] Real Ktor HTTP transport in `shared:networking` (`KtorSyncTransport` implements `SyncTransport`; WebSocket event ingestion lands with the kitchen UI)

## Slice 7 — Notifications / Printing
- [ ] Push
- [ ] SMS
- [ ] Receipt printing
- [ ] Retry/failure handling

## Slice 8 — Hardening
- [ ] Security review
- [ ] Accessibility review
- [ ] Load tests
- [ ] Monitoring
- [ ] Backup/restore
- [ ] Production deployment
