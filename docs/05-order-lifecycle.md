# Order Lifecycle

## States

```text
DRAFT
→ PENDING_PAYMENT
→ PAYMENT_AUTHORIZED
→ PAID
→ QUEUED
→ ACCEPTED
→ PREPARING
→ DELAYED
→ READY
→ COMPLETED
```

## Terminal states

- REJECTED
- CANCELLED
- REFUNDED
- EXPIRED

## Rules

- The server is authoritative.
- Payment must be verified before order processing unless pay-later is explicitly enabled.
- Delay actions must include a new ETA.
- Every transition must create a timeline event.
- Rejection must include a reason code.
- Refunds must link to the original payment.

## Timeline event fields

- timestamp
- actor type
- actor id
- previous state
- new state
- reason code
- metadata
