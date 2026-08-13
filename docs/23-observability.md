# Observability

## Structured logs

Include:
- timestamp
- level
- service
- requestId
- actorId where permitted
- orderId where relevant
- eventId where relevant
- error code

Never log secrets or card data.

## Metrics

Track orders, payment success/failure, acceptance time, preparation time, delay/rejection rate, notification delivery, WebSocket connections/reconnects, sync backlog, and printer failures.

## Alerts

Alert on payment webhook failures, abnormal payment failure rates, database failures, event/queue backlog, excessive disconnects, and notification degradation.
