# API Contract

## Principles

- Version all endpoints.
- Use typed DTOs.
- Validate on the server.
- Make mutations idempotent.
- Keep customer, kitchen, and admin APIs separate.

## Public endpoints

```text
GET /v1/restaurant/status
GET /v1/menu
GET /v1/menu/categories
GET /v1/menu/products/{id}
POST /v1/cart
POST /v1/orders
GET /v1/orders/{orderId}
GET /v1/orders/{orderId}/timeline
POST /v1/payments/create
POST /v1/payments/webhook/{provider}
```

## Kitchen endpoints

```text
GET /v1/kitchen/orders
POST /v1/kitchen/orders/{id}/accept
POST /v1/kitchen/orders/{id}/reject
POST /v1/kitchen/orders/{id}/delay
POST /v1/kitchen/orders/{id}/ready
POST /v1/kitchen/orders/{id}/complete
```

## Admin endpoints

```text
GET /v1/admin/orders
GET /v1/admin/products
POST /v1/admin/products
PATCH /v1/admin/products/{id}
PATCH /v1/admin/categories/{id}
PATCH /v1/admin/opening-hours
PATCH /v1/admin/restaurant/settings
```

## WebSocket channels

- /v1/ws/orders
- /v1/ws/kitchen
- /v1/ws/admin
