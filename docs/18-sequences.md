# Sequence Flows

## Customer order

```text
Customer
  -> API: create draft
  -> API: calculate server total
  -> Payment Provider: create payment session
  -> Customer: complete payment
  -> Payment Provider: signed webhook
  -> API: verify webhook
  -> DB: mark PAID
  -> Event Stream: order.paid
  -> Kitchen: show order
```

## Kitchen accept / delay

```text
Kitchen -> API: action + ETA/reason
API -> DB: validate transition
DB -> Event Stream: order.updated
Event Stream -> Customer: live update
Event Stream -> Notification Worker: push/SMS
```

## Reconnect

```text
Client loses connection
  -> local store continues
  -> mutations enter outbox
  -> connection returns
  -> authenticate
  -> request missed events
  -> replay valid outbox actions
  -> reconcile state
```
