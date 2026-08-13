# Payments

## Principle

Payments must be isolated from restaurant logic.

## Requirements

- Client never marks an order as paid.
- Backend verifies payment by webhook or signed callback.
- Orders move to paid only after verification.
- Provider must be replaceable through an interface.
- Webhook replay protection is mandatory.
- All payment state changes must be audited.

## Provider interface

- create payment session
- verify payment result
- capture payment
- refund payment
- cancel payment
- reconcile webhook

## Stored payment data

- provider
- provider reference
- order id
- amount
- currency
- status
- timestamps
- refund linkage
