# Database

## Primary store

PostgreSQL is the canonical data store.

## Core tables

- restaurants
- locations
- users
- roles
- categories
- products
- modifier_groups
- modifier_options
- carts
- cart_items
- orders
- order_items
- payments
- notifications
- order_timeline_events
- device_sessions

## Rules

- Use migration files.
- Keep order snapshots immutable.
- Index order lookup fields.
- Store audit timestamps on every critical record.
- Keep payment records separate from order records.
