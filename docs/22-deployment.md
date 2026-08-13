# Deployment

## Initial topology

```text
Internet
  |
Reverse proxy / TLS
  |
Ktor API
  |---- PostgreSQL
  |---- Optional Redis
  |---- Worker
```

## Requirements

- HTTPS
- automated PostgreSQL backups
- explicit database migration strategy
- health/readiness endpoints
- structured logs
- metrics
- error reporting
- rollback procedure

## Health endpoints

```text
GET /health/live
GET /health/ready
```

Readiness must verify required dependencies.

Backups must be periodically restored outside production to verify recovery.
