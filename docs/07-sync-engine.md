# Sync Engine

## Goals

- Realtime by default
- Offline-tolerant in kitchen
- Backend authoritative
- Deterministic state reconciliation

## Transport

- WebSockets for live updates
- HTTP for initial load and recovery
- Local persistence for offline replay

## Event types

- order.created
- order.paid
- order.accepted
- order.preparing
- order.delayed
- order.ready
- order.completed
- order.rejected
- order.cancelled
- menu.updated
- product.sold_out
- restaurant.opened
- restaurant.closed

## Sync rules

- Clients subscribe to events.
- Local caches are optimistic but never authoritative.
- Queued offline actions must be replayed in order.
- Duplicate actions must be idempotent.
- Stale actions must fail clearly.
