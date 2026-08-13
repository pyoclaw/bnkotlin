# Configuration

## Environments

- local
- test
- staging
- production

## Server variables

```text
SERVER_PORT
DATABASE_URL
JWT_SECRET
JWT_ISSUER
JWT_AUDIENCE
PAYMENT_PROVIDER
PAYMENT_SECRET
PAYMENT_WEBHOOK_SECRET
SMS_PROVIDER
PUSH_PROVIDER
LOG_LEVEL
```

## Client configuration

Only non-secret configuration belongs in clients:
- API base URL
- WebSocket URL
- restaurant id
- feature flags
- public payment configuration where required

Secrets never belong in client binaries.
