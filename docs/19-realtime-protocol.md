# Realtime Protocol

## Event envelope

```json
{
  "eventId": "uuid",
  "type": "order.accepted",
  "aggregateId": "order-id",
  "version": 42,
  "occurredAt": "2026-01-01T12:00:00Z",
  "payload": {}
}
```

## Rules

- `eventId` uniquely identifies an event.
- `aggregateId` identifies the affected aggregate.
- `version` increases monotonically per aggregate.
- Duplicate events are ignored after application.
- Version gaps trigger recovery.
- The server is authoritative.

## WebSocket lifecycle

1. Connect.
2. Authenticate.
3. Subscribe.
4. Receive heartbeats and events.
5. Persist last cursor/version.
6. Reconnect when stale.
7. Recover missed events.
8. Resume live stream.
