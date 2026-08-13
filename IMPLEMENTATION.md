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
- [ ] Offline mutation replay (reconnect + idempotent retry)
- [ ] Event recovery
- [ ] Conflict tests

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
