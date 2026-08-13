# Development Guide

## Prerequisites

- JDK 21+
- Android Studio with Kotlin Multiplatform support
- Xcode for iOS builds
- Docker for local services

## Implementation order

1. Create Gradle multiplatform modules.
2. Implement shared domain models and state machine.
3. Add PostgreSQL schema and migrations.
4. Implement Ktor API.
5. Implement menu and cart.
6. Add payment adapter and webhook verification.
7. Implement kitchen workflow.
8. Add WebSocket events.
9. Add offline persistence and reconciliation.
10. Add notifications and printing.

Build one vertical slice at a time: domain -> persistence -> API -> UI -> tests.

## Environment

Never commit secrets. Use environment variables for database, JWT, payment, push, and SMS credentials.

## Git

Keep commits small and feature-oriented. Avoid broad refactors while implementing a feature.
