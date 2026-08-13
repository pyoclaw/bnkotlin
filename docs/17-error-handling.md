# Error Handling

Errors must be typed, explicit, and recoverable where possible.

## API shape

```json
{
  "code": "ORDER_STATE_CONFLICT",
  "message": "Order can no longer be delayed.",
  "requestId": "..."
}
```

## Categories

- Validation: invalid input; show field-level feedback.
- Authentication: refresh or sign in again.
- Authorization: authenticated but not permitted.
- Conflict: requested state is no longer valid.
- Payment: failed, expired, cancelled, or needs recovery.
- Network: retry or enter offline mode where supported.
- Internal: expose a safe message and log details server-side.

Never silently discard a kitchen action.
