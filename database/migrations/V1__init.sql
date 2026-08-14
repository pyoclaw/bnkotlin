-- Brød & Fyld Next — initial schema (Flyway V1).
-- Canonical source of truth for menus, orders, payments and audit timeline.
-- See docs/12-database.md and docs/05-order-lifecycle.md.

CREATE TABLE restaurants (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    currency   TEXT NOT NULL DEFAULT 'DKK',
    timezone   TEXT NOT NULL DEFAULT 'Europe/Copenhagen',
    open       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id            TEXT PRIMARY KEY,
    restaurant_id TEXT NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    description   TEXT,
    sort_order    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id               TEXT PRIMARY KEY,
    restaurant_id    TEXT NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    category_id      TEXT NOT NULL REFERENCES categories (id),
    name             TEXT NOT NULL,
    description      TEXT,
    base_price_minor BIGINT NOT NULL,
    available        BOOLEAN NOT NULL DEFAULT TRUE,
    sold_out         BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE modifier_groups (
    id            TEXT PRIMARY KEY,
    product_id    TEXT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    required      BOOLEAN NOT NULL DEFAULT FALSE,
    min_selection INTEGER NOT NULL DEFAULT 0,
    max_selection INTEGER NOT NULL DEFAULT 1,
    sort_order    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE modifier_options (
    id                TEXT PRIMARY KEY,
    modifier_group_id TEXT NOT NULL REFERENCES modifier_groups (id) ON DELETE CASCADE,
    name              TEXT NOT NULL,
    price_delta_minor BIGINT NOT NULL DEFAULT 0
);

-- Orders and their immutable item snapshots.
CREATE TABLE orders (
    id            TEXT PRIMARY KEY,
    restaurant_id TEXT NOT NULL REFERENCES restaurants (id),
    customer_id   TEXT,
    currency      TEXT NOT NULL DEFAULT 'DKK',
    total_minor   BIGINT NOT NULL,
    state         TEXT NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX orders_restaurant_state_idx ON orders (restaurant_id, state);

CREATE TABLE order_items (
    id               TEXT PRIMARY KEY,
    order_id         TEXT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id       TEXT NOT NULL,
    name             TEXT NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    quantity         INTEGER NOT NULL,
    line_total_minor BIGINT NOT NULL,
    modifier_names   JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX order_items_order_idx ON order_items (order_id);

-- Audit timeline. event_id is unique so event application is idempotent.
CREATE TABLE order_timeline_events (
    id             BIGSERIAL PRIMARY KEY,
    order_id       TEXT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    event_id       TEXT NOT NULL UNIQUE,
    occurred_at    TIMESTAMPTZ NOT NULL,
    actor_type     TEXT NOT NULL,
    actor_id       TEXT,
    previous_state TEXT NOT NULL,
    new_state      TEXT NOT NULL,
    reason_code    TEXT,
    metadata       JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX order_timeline_order_idx ON order_timeline_events (order_id, id);

-- Payments are kept separate from orders (docs/08-payments.md).
CREATE TABLE payments (
    id                 TEXT PRIMARY KEY,
    order_id           TEXT NOT NULL REFERENCES orders (id),
    provider           TEXT NOT NULL,
    provider_reference TEXT,
    amount_minor       BIGINT NOT NULL,
    currency           TEXT NOT NULL DEFAULT 'DKK',
    status             TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX payments_order_idx ON payments (order_id);
