# AI Development Rules

## Core rules

- Keep all domain logic in shared Kotlin modules.
- Keep platform-specific code thin.
- Never duplicate business rules across clients.
- Treat the backend as the source of truth.
- Prefer immutable models.
- Use explicit state machines for order flow.
- Make mutations idempotent where possible.
- Validate on both client and server.
- Do not couple payment logic to order logic.
- Do not introduce a second frontend stack.

## Implementation rules

- Use Compose Multiplatform for shared UI.
- Use Ktor for API, auth, and realtime transport.
- Use PostgreSQL as canonical persistence.
- Use WebSockets for live updates.
- Use local persistence for offline kitchen resilience.
- Keep APIs versioned.
- Keep DTOs typed and minimal.
- Write tests for state transitions, payment callbacks, and sync reconciliation.

## Delivery rule

When in doubt, choose the smallest implementation that preserves the architecture.
