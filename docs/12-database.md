# Database

## Primary store

PostgreSQL is the canonical data store. The backend accesses it through
JetBrains Exposed over a bounded HikariCP pool; Flyway applies versioned
migrations on startup. SQL is never written in route handlers — repositories
own all SQL (see docs/02-architecture.md).

## Migrations

Schema changes are versioned Flyway migrations under `database/migrations/`
(e.g. `V1__init.sql`, `V2__outbox.sql`). Schema is not created ad hoc at
startup in production; migrations are committed to Git and applied
reproducibly.

## Connection management

A single HikariCP pool (`maximumPoolSize = 5`) is shared by Flyway and
Exposed. Pool size stays small so `connections × instances` remains within
PostgreSQL capacity.

## Core tables

- restaurants
- categories
- products
- modifier_groups
- modifier_options
- orders
- order_items
- order_timeline_events
- payments
- outbox_events

Planned (later slices): locations, users, roles, carts, cart_items,
notifications, device_sessions.

## Constraints and indexes

- `orders.id` is the primary key; `(restaurant_id, state)` and lookup columns
  are indexed.
- `order_items.order_id` and `order_timeline_events.order_id` are indexed.
- `order_timeline_events.event_id` and `outbox_events.event_id` are unique so
  event application is idempotent.
- `outbox_events` is indexed on `(published_at, created_at)` for the delivery
  worker and `(aggregate_id, aggregate_version)` for recovery.

## Order versioning (optimistic concurrency)

Every order carries a monotonically increasing `version`. A kitchen or customer
mutation reads the current version, then the repository applies the write with
`UPDATE ... WHERE id = ? AND version = expectedVersion`. If zero rows match,
the backend rejects the stale mutation with `409 concurrent_modification`
rather than silently overwriting a newer state.

## Transactions and outbox

Order mutations are single PostgreSQL transactions:

```text
validate transition
  -> update order + increment version
  -> rewrite item snapshot / audit timeline
  -> insert outbox_events row(s)
  -> commit
```

The outbox row is committed with the order change, so a committed change always
has a durable, deliverable realtime event. The in-process relay publishes to
the WebSocket fan-out only after commit; a durable worker (retry, `attempts`,
`published_at`) is the next step and can be added without changing the schema.

## Local (client) database

SQLDelight over SQLite holds only what a client needs: menu/customer/kitchen
caches, local order timeline, sync cursor and a pending-mutation outbox
(`shared:sync`). It is never the authoritative store; SQLite is not a mirror of
PostgreSQL.

## Payments

`payments` stores provider references and status, never raw card data (PAN,
CVV, track data). The provider remains responsible for sensitive card data
(docs/08-payments.md).

## Rules

- Keep order snapshots immutable.
- Keep payment records separate from order records.
- Store audit timestamps on every critical record.
- Test PostgreSQL-specific behavior (transactions, constraints, locking,
  timestamps, optimistic locking) against real PostgreSQL, not H2.
