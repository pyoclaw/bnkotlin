# Security

## Authentication

- JWT or equivalent token auth
- Refresh tokens
- Short-lived access tokens
- Role-based access control

## Transport

- HTTPS only
- Signed webhooks
- Secure session handling

## Data

- Never store raw card data (PAN, CVV, track data); the payment provider owns it
- Minimize personal data
- Encrypt secrets
- Keep audit logs

## Data integrity

- Order mutations are optimistic-concurrency protected (`order.version`); stale
  writes are rejected with `409 concurrent_modification`.
- Order state + outbox events commit atomically, so realtime events cannot
  represent uncommitted state.
- Critical mutations are idempotent (unique event ids / outbox `event_id`).
- Clients can never overwrite authoritative state arbitrarily.

## Roles

- customer
- kitchen_staff
- manager
- admin
