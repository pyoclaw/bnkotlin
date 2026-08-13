# Architecture Decision Records

## ADR-001: Kotlin-first

Use Kotlin Multiplatform for shared domain, networking, validation, and UI where practical. This reduces duplication across targets.

## ADR-002: Compose Multiplatform

Use Compose Multiplatform for shared UI and design-system components. Treat Web/Wasm as a deliberate target and validate browser/SEO requirements before depending on it for every public page.

## ADR-003: Ktor

Use Ktor for backend APIs and realtime transport because it fits the Kotlin ecosystem and coroutine-based architecture.

## ADR-004: PostgreSQL

Use PostgreSQL as the canonical source of truth for orders, payments, menus, and audit history.

## ADR-005: WebSockets

Use WebSockets for live order/kitchen updates, with HTTP for initial load and recovery.

## ADR-006: Offline kitchen

Use local persistence plus an outbound action queue so temporary network loss does not stop kitchen operations.

## ADR-007: Payment abstraction

Hide payment providers behind a backend interface so the operational domain remains provider-independent.
