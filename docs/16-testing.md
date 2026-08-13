# Testing Strategy

## Unit tests

Cover:
- order state transitions
- pricing and modifier validation
- pickup-time validation
- payment state transitions
- notification routing
- sync conflict rules

## Integration tests

Cover Ktor endpoints, PostgreSQL persistence, webhook verification, WebSocket delivery, authentication, and authorization.

## End-to-end

Test the complete path:

`browse -> customize -> order -> pay -> kitchen accept -> delay -> ready -> complete`

## Offline

Test connection loss, queued actions, duplicate events, reconnect, stale actions, and reconciliation.

## Rule

Anything that can lose money, lose an order, or create a duplicate action must have automated tests.
