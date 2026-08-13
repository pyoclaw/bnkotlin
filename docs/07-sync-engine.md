# Sync Engine

## Goals

- Realtime by default
- Offline-tolerant in kitchen
- Backend authoritative
- Deterministic state reconciliation

## Transport

- WebSockets for live updates
- HTTP for initial load and recovery
- Local persistence (SQLDelight/SQLite) for offline replay

## Event types

- order.created
- order.submitted
- order.payment_authorized
- order.paid
- order.queued
- order.accepted
- order.preparing
- order.delayed
- order.ready
- order.completed
- order.rejected
- order.cancelled
- order.refunded
- order.expired
- menu.updated
- product.sold_out
- restaurant.opened
- restaurant.closed

## Commit → publish flow

Realtime events must never represent uncommitted state:

```text
API request
  -> validate
  -> BEGIN TRANSACTION
  -> update PostgreSQL
  -> insert outbox_events row
  -> COMMIT
  -> publish realtime event (in-process relay)
```

A durable worker can later poll unpublished `outbox_events` rows, publish them
and mark `published_at`, retrying with `attempts`. No Redis/Kafka is required
for the MVP.

## Order versioning

Each order mutation carries the `version` it was read at. The repository
applies the write only if the stored version still matches; otherwise the
backend rejects the stale mutation. This prevents two kitchen devices from
silently overwriting each other.

## Client outbox (offline)

The kitchen (and customer) client persists pending mutations in its local
SQLDelight `local_outbox` table. Pressing ACCEPT updates the local cache
immediately, then the outbox replays the mutation to the server when the
network returns. Mutations carry a client-generated `mutation_id` so retries
are idempotent; the server recognizes duplicates.

## Sync rules

- Clients subscribe to events.
- Local caches are optimistic but never authoritative.
- Queued offline actions must be replayed in order.
- Duplicate actions must be idempotent.
- Stale actions must fail clearly.
