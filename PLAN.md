# PLAN — Brød & Fyld Next (RestaurantOS)

Status: Milestone 1 (foundation + shared domain) and the order vertical slice
(persistence + API) are implemented and building. The customer → kitchen
realtime loop is the next milestone (Slices 6–7).

## Current state (recovered)

- **Done:** Gradle/KMP foundation, `shared:domain` (order state machine,
  pricing, order factory), `backend:ktor-api` (health/status/menu/order/kitchen
  endpoints + WebSocket skeleton), Compose desktop smoke app.
- **Persistence:** Flyway migrations (`database/migrations`) + a JDBC
  `PostgresOrderRepository`. Real-Postgres tests are gated on `DATABASE_URL`
  and run in CI (GitHub Actions with a Postgres service). Locally, without a
  database, the API falls back to `InMemoryOrderRepository` (dev only) so the
  build stays green; this fallback is never used in production.
- **CI:** `.github/workflows/ci.yml` runs `./gradlew build` with PostgreSQL.
- **Not yet done:** real payment provider, kitchen/customer UI, realtime event
  stream, offline outbox, notifications, printing, hardening.

This document records architecture findings, toolchain decisions, the
implementation sequence, risks and open questions. Keep it in sync with code
and the ADRs; update it whenever a decision materially changes.

## 1. Architecture findings

The existing spec (`docs/`) is coherent and was validated against the current
Kotlin ecosystem. Findings that shaped this plan:

- **Kotlin-first end-to-end is viable.** Android, iOS and desktop targets are
  stable in Kotlin Multiplatform. Compose Multiplatform for Web/Wasm is Beta
  (see `docs/02-architecture.md` and the official compatibility page), so the
  web target is treated as a deliberate, lower-maturity target — not assumed
  to be at parity with native.
- **The order lifecycle is the core domain.** It is modelled as a strict,
  centrally-defined state machine (see `shared/domain`). No UI invents states.
- **Payments stay isolated.** `PaymentProvider` is a backend-side contract;
  the client never marks an order paid.
- **Realtime is WebSocket-first**, with HTTP for initial load/recovery and an
  explicit reconnect/recovery path (Slice 7).
- **Offline kitchen** uses local persistence plus an outbox, not ad-hoc
  try/catch (Slice 8).

## 2. Toolchain (DECIDED — pinned against official docs, Aug 2026)

| Component | Version | Rationale |
|---|---|---|
| Kotlin | 2.4.10 | Current stable (Kotlin 2.4.20 is still RC). |
| Compose Multiplatform | 1.11.1 | Latest stable (1.12.x is still RC). |
| Compose Compiler plugin | 2.4.10 | Must match the Kotlin plugin version. |
| Ktor | 3.5.2 | Latest stable server/client. |
| Gradle | 9.5.0 | Within Kotlin 2.4.10's tested range (7.6.3–9.5.0). |
| JDK | 21 (Temurin LTS) | `docs/15-development.md` requires 21+; Compose desktop needs 17+. |
| kotlinx.serialization | 1.11.0 | Latest stable. |
| kotlinx.coroutines | 1.11.0 | Latest stable. |
| kotlinx-datetime | 0.6.2 | Multiplatform instants. Pinned: 0.7+ moved Instant/Clock into `kotlin.time` (stdlib) and dropped `Clock.System` + built-in serializers; we migrate once the `kotlin.time` serialization story is clean. |
| SQLDelight | 2.3.2 | Chosen for local persistence (offline kitchen); pinned, not yet wired. |
| AGP | (not yet used) | Kotlin 2.4.x supports 8.5.2–9.1.0; exact version chosen when the Android target lands. |
| Xcode | 26.4 (macOS only) | Required for the iOS target; not available in this Linux workspace. |

Versions are centralized in `gradle/libs.versions.toml`.

## 3. Repository structure (DECIDED)

Follows the official Kotlin Multiplatform convention of Gradle modules under
`shared/` + `backend/` + `apps/`, and deliberately does **not** create a module
per domain concept. Modules are added only when a real ownership boundary or
SDK-specific toolchain exists.

```
brod-og-fyld/
  shared/
    domain/        KMP: models, order state machine, pricing, payments (common code)
    ui/            KMP + Compose: shared design system and screens (desktop-first for now)
    sync/          (planned, Slice 7/8) realtime client + offline outbox
    networking/    (planned) Ktor client wrapper
    payments/      (planned) shared payment types (kept provider-independent)
  backend/
    ktor-api/      Ktor server: HTTP + WebSocket API
    worker/        (planned) background worker (notifications, printing)
  apps/
    customer/      (planned) Android + iOS + web shell around shared UI
    kitchen/       (planned) desktop kitchen app
    admin/         (planned) desktop admin app
  database/        (planned) PostgreSQL migrations
  deploy/          (planned) deployment config
```

Milestone 1 only creates `shared:domain`, `shared:ui` and `backend:ktor-api`.
The remaining directories exist as placeholders and are filled by later slices.

## 4. Dependency decisions (DECIDED)

- **Persistence:** PostgreSQL for canonical state (Slice 3). SQLDelight/SQLite
  for offline kitchen cache and outbox (Slice 8). No Redis, no event broker,
  no Kafka, no GraphQL — none solve a demonstrated MVP problem.
- **Serialization:** kotlinx.serialization for DTOs and the event envelope.
- **Money:** integer minor units in a `Money` value class; one currency per
  cart/order (DKK initially). No floating point.
- **IDs/time:** event/entity IDs and clock are supplied at the platform
  boundary (JVM supplies `UUID`); domain code stays deterministic and testable.
- **Tax:** no VAT/tax model yet. Danish VAT (25%) handling is deferred.

## 5. Implementation sequence (vertical slices)

1. **Slice 1 — Foundation** (this milestone): repo, Gradle/KMP structure,
   Compose Multiplatform wiring, Ktor skeleton, version catalog, CI-ready
   build, domain tests.
2. **Slice 2 — Domain:** Restaurant, Product, Menu, Cart, Order, state
   machine, pricing, validation (started here; completed with full menu/pricing).
3. **Slice 3 — Database/API:** PostgreSQL, migrations, repositories, order
   persistence, DTOs. *(done: migrations, JDBC repository, order + kitchen API)*
4. **Slice 4 — Customer:** home/menu/product/cart/checkout skeleton.
5. **Slice 5 — Payments:** provider implementation, signed webhook, idempotency.
6. **Slice 6 — Kitchen:** queue, actions, ETA, timeline.
7. **Slice 7 — Realtime:** WS auth, subscriptions, events, reconnect, recovery.
8. **Slice 8 — Offline:** local database, cache, outbox, reconciliation.
9. **Slice 9 — Notifications/printing:** push, SMS, receipt printing.
10. **Slice 10 — Hardening:** security, accessibility, observability, deploy,
    backup/recovery.

## 6. Risks

- **Compose Web/Wasm maturity (Beta).** The public ordering page must be
  mobile-first and accessible. If Wasm loading or browser compatibility becomes
  a blocker for the public funnel, we re-evaluate a thin web shell — but we do
  **not** introduce React/Next.js pre-emptively. See ADR-002.
- **Android/iOS/Web targets cannot be built in this Linux workspace** (no
  Android SDK, no Xcode). Milestone 1 verifies the JVM targets only; native
  targets are verified in CI once SDKs are provisioned.
- **No local PostgreSQL in this workspace** means Slice 3 needs either a
  Docker/CI service container or an in-memory test double for unit tests.
- **Payment provider selection** is still open; the abstraction must be
  validated against a real provider (see Open questions).

## 7. Open questions

- [ ] **Payment provider (OPEN):** which provider to integrate first. Decision
      affects the webhook/signature API in Slice 5.
- [ ] **SMS/push providers (OPEN):** not yet chosen.
- [ ] **Tax/VAT handling (OPEN):** display price-inclusive (Danish norm) vs
      price-plus-tax.
- [ ] **Auth strategy for kitchen/admin (OPEN):** JWT-based session vs
      passwordless PIN for shared kitchen devices.
- [ ] **Receipt printing (OPEN):** ESC/POS via a print service, or third-party
      print gateway.
- [ ] **GitHub repo visibility (OPEN):** the connected repo is currently
      public. Confirm whether it should be made private.

## 8. First milestone (this slice)

**Definition of success:** a green, reproducible Gradle build that proves the
foundation and the core domain, and serves as the base for the customer order
vertical slice.

Done in this milestone:

- Gradle Multiplatform workspace with a version catalog and Gradle wrapper.
- `shared:domain` — models, central `OrderStateMachine`, pricing/validation,
  payment abstraction, with unit tests.
- `backend:ktor-api` — Ktor server with `/health/live`, `/health/ready`,
  `/v1/restaurant/status`, `/v1/menu` and a WebSocket `/v1/ws/orders` skeleton,
  with integration tests.
- `shared:ui` — Compose Multiplatform desktop smoke app proving the UI toolchain.

The full customer → kitchen → realtime order loop is the **next** milestone
(Slices 2–7), not this one.

## 9. Verification

```
./gradlew build
```

runs all tests (domain + backend) on the JVM target.
