# Brød & Fyld Next

Kotlin-first restaurant operating platform for pickup ordering, realtime kitchen operations, and future delivery expansion.

## Stack

- Kotlin Multiplatform
- Compose Multiplatform
- Ktor
- PostgreSQL
- SQLDelight / SQLite for offline cache
- WebSockets for realtime sync

## Repo layout

```text
apps/
  customer/
  kitchen/
  admin/
backend/
  ktor-api/
  worker/
shared/
  domain/
  ui/
  sync/
  networking/
  payments/
docs/
```

## Working rules

- Backend is the source of truth.
- Shared Kotlin owns business logic.
- Compose owns UI.
- Realtime first.
- Offline-tolerant kitchen workflows.
- Payment providers stay behind an interface.

## Agent-oriented documentation

Start with:

1. `AGENTS.md`
2. `IMPLEMENTATION.md`
3. `docs/02-architecture.md`
4. `docs/04-domain-model.md`
5. `docs/05-order-lifecycle.md`
6. `docs/07-sync-engine.md`
7. `docs/08-payments.md`

Supporting implementation specifications are in `docs/15-development.md` through `docs/25-definition-of-done.md`.
