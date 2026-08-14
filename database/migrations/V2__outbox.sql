-- Brød & Fyld Next — transactional outbox (Flyway V2).
-- Order mutations write an outbox row in the same transaction as the order
-- change, so a committed change always has a durable, deliverable event
-- (docs/07-sync-engine.md, docs/12-database.md). A worker publishes rows and
-- marks them published_at; attempts tracks delivery retries.

CREATE TABLE outbox_events (
    id                BIGSERIAL PRIMARY KEY,
    event_id          TEXT NOT NULL UNIQUE,
    aggregate_type    TEXT NOT NULL,
    aggregate_id      TEXT NOT NULL,
    event_type        TEXT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL,
    published_at      TIMESTAMPTZ,
    attempts          INTEGER NOT NULL DEFAULT 0
);

-- The worker polls unpublished rows by created_at.
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (published_at, created_at);
-- Recovery queries fetch events for one aggregate in version order.
CREATE INDEX outbox_events_aggregate_idx ON outbox_events (aggregate_id, aggregate_version);
