package com.brodogfyld.sync

/** Outcome of applying one outbox mutation on the server. */
sealed interface MutationResult {
    /** The server accepted (or idempotently acknowledged) the mutation. */
    data class Applied(val order: RemoteOrder) : MutationResult

    /**
     * The mutation can never be applied: the order no longer exists, or its
     * state/version has moved on. It is dropped and the cache reconciled from
     * the server. [code] is the server's error code (e.g. concurrent_modification,
     * invalid_transition, order_not_found).
     */
    data class Stale(val code: String) : MutationResult

    /** Transient failure (network, 5xx); the mutation stays queued for retry. */
    data class Retryable(val message: String) : MutationResult
}

/**
 * Transport seam between the offline sync engine and the backend. The engine is
 * transport-agnostic so its replay/recovery behaviour is testable without a
 * network; the real Ktor client implementation lives in `shared:networking`
 * (wired when the kitchen UI lands). HTTP is used for recovery and replay,
 * WebSockets for live events (docs/07-sync-engine.md).
 */
interface SyncTransport {
    suspend fun applyMutation(mutation: OutboxMutation): MutationResult

    suspend fun fetchOrder(orderId: String): RemoteOrder?

    suspend fun fetchKitchenOrders(restaurantId: String): List<RemoteOrder>
}
