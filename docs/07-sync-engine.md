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

## Idempotency (implemented)

A client mutation id doubles as the server timeline event id. On a kitchen
action the backend (`OrderService.kitchenMutate`) first checks whether a
timeline event with that id already exists for the order; if it does, the
current order is returned unchanged instead of re-applying the state machine.
Because `order_timeline_events.event_id` is `UNIQUE`, this is durable across
restarts and immune to the optimistic-version race. Mutation ids must be
globally unique (they are client-generated UUIDs).

## Offline sync engine (implemented)

`shared:sync` ships a transport-agnostic `OrderSyncEngine` over the
SQLDelight cache/outbox:

- **Optimistic apply** — a local kitchen action runs the shared
  `OrderStateMachine` against the cached projection, updates the cache, and
  enqueues an `OutboxMutation` with a fresh mutation id.
- **Replay** — `local_outbox` entries are replayed in strict FIFO order (an
  `AUTOINCREMENT` `seq` column, not `created_at`, which can tie). Applied
  mutations are acknowledged and removed; stale mutations (conflict, missing
  order) are dropped and the cache reconciled from the server; a transient
  failure stops the pass so later mutations are never replayed out of order.
- **Event recovery** — realtime events apply only when the version is exactly
  the next one (`cached.version + 1`); duplicates (<= cached version) are
  ignored and gaps trigger a full fetch of that order.
- **Full resync** — `recover(restaurantId)` replaces the cache with the
  server's kitchen-active list, preserving orders that still have pending
  outbox mutations (those are reconciled by replay instead).

The engine depends only on a `SyncTransport` interface (apply mutation, fetch
order, fetch kitchen orders). The real HTTP transport lives in
`shared:networking` (`KtorSyncTransport`), and `SyncCoordinator.synchronize()`
composes replay + recovery into the canonical reconnect action. WebSocket
event ingestion lands with the kitchen UI (Slice 7).

## Sync rules

- Clients subscribe to events.
- Local caches are optimistic but never authoritative.
- Queued offline actions must be replayed in order.
- Duplicate actions must be idempotent.
- Stale actions must fail clearly.
