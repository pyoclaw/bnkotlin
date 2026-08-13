# Implementation Plan

Build in vertical slices rather than completing entire layers independently.

## Slice 1 — Foundation
- [ ] Gradle multiplatform structure
- [ ] Shared domain module
- [ ] Design-system skeleton
- [ ] Ktor server
- [ ] PostgreSQL connection
- [ ] CI build

## Slice 2 — Menu
- [ ] Schema
- [ ] API
- [ ] Shared models
- [ ] Customer menu UI
- [ ] Tests

## Slice 3 — Order
- [ ] Cart
- [ ] Order state machine
- [ ] Persistence
- [ ] API
- [ ] Checkout UI

## Slice 4 — Payment
- [ ] PaymentProvider interface
- [ ] Provider adapter
- [ ] Signed webhook
- [ ] Idempotency
- [ ] Payment tests

## Slice 5 — Kitchen
- [ ] Kitchen API
- [ ] Kitchen UI
- [ ] Realtime stream
- [ ] State actions
- [ ] Audit timeline

## Slice 6 — Offline
- [ ] Local cache
- [ ] Outbox
- [ ] Reconnect
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
