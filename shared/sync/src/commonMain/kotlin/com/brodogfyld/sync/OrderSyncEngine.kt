package com.brodogfyld.sync

import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderStateMachine
import com.brodogfyld.domain.order.OrderTransitionException
import com.brodogfyld.domain.order.TransitionFailure
import com.brodogfyld.sync.db.Cached_order
import com.brodogfyld.sync.db.KitchenDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Why a local kitchen action was rejected before being queued. */
enum class LocalRejectReason {
    NOT_CACHED,
    ILLEGAL_TRANSITION,
    TERMINAL_STATE,
    MISSING_REASON_CODE,
    INVALID_ETA,
}

sealed interface LocalActionResult {
    data class Applied(val version: Long) : LocalActionResult
    data class Rejected(val reason: LocalRejectReason) : LocalActionResult
}

/** Per-mutation result of an outbox replay pass. */
sealed interface ReplayOutcome {
    data class Applied(val mutationId: String, val orderId: String) : ReplayOutcome

    /** The server rejected the mutation permanently; it is dropped and reconciled. */
    data class Stale(val mutationId: String, val orderId: String, val code: String) : ReplayOutcome

    /** Skipped because an earlier mutation on the same order went stale. */
    data class Superseded(val mutationId: String, val orderId: String) : ReplayOutcome

    /** Transient failure; the mutation stays queued for the next pass. */
    data class Retryable(val mutationId: String, val orderId: String, val message: String) : ReplayOutcome
}

data class ReplayReport(val outcomes: List<ReplayOutcome>) {
    val appliedCount: Int get() = outcomes.count { it is ReplayOutcome.Applied }
    val staleCount: Int get() = outcomes.count { it is ReplayOutcome.Stale }
    val pendingCount: Int get() = outcomes.count { it is ReplayOutcome.Retryable }
}

/** Result of applying a single realtime event to the local cache. */
sealed interface EventResult {
    data object Applied : EventResult
    data object Duplicate : EventResult
    data object Ignored : EventResult
    data class Recovered(val orderId: String) : EventResult
}

data class RecoverResult(val replaced: Int, val removed: Int)

/**
 * Offline-first kitchen sync engine (docs/07-sync-engine.md).
 *
 * Local actions are applied optimistically through the shared
 * [OrderStateMachine], persisted to the SQLDelight cache, and queued in
 * `local_outbox` with a client-generated idempotency key. On reconnect the
 * outbox replays in order (duplicates are deduplicated by the server via the
 * mutation id, which doubles as the timeline event id), and the cache is
 * reconciled from the authoritative server. Realtime events are applied only
 * when in sequence; duplicates are ignored and version gaps trigger a full
 * resync of that order.
 *
 * The engine is transport-agnostic: [transport] is the only I/O seam, so all
 * reconciliation behaviour is unit-testable against a fake.
 */
class OrderSyncEngine(
    private val database: KitchenDatabase,
    private val transport: SyncTransport,
    private val idGenerator: () -> String,
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val queries get() = database.kitchenQueries

    // --- optimistic offline mutation -----------------------------------------

    suspend fun localAction(
        orderId: String,
        action: KitchenAction,
        actorId: String? = null,
        reasonCode: String? = null,
        newEta: Instant? = null,
    ): LocalActionResult {
        val cached = queries.selectOrder(orderId).executeAsOneOrNull()
            ?: return LocalActionResult.Rejected(LocalRejectReason.NOT_CACHED)
        val current = cached.toOrder()
        val mutationId = idGenerator()
        val transition = when (action) {
            KitchenAction.ACCEPT ->
                OrderStateMachine.accept(current, actorId ?: "unknown", mutationId, clock())
            KitchenAction.REJECT ->
                OrderStateMachine.reject(current, actorId ?: "unknown", reasonCode ?: "", mutationId, clock())
            KitchenAction.DELAY ->
                if (newEta == null) {
                    Result.failure(OrderTransitionException(TransitionFailure.INVALID_ETA, current.state, OrderState.DELAYED))
                } else {
                    OrderStateMachine.delay(current, actorId ?: "unknown", newEta, mutationId, clock())
                }
            KitchenAction.READY ->
                OrderStateMachine.ready(current, actorId ?: "unknown", mutationId, clock())
            KitchenAction.COMPLETE ->
                OrderStateMachine.complete(current, actorId ?: "unknown", mutationId, clock())
        }
        return transition.fold(
            onSuccess = { next ->
                queries.upsertOrder(
                    id = next.id,
                    restaurant_id = next.restaurantId,
                    state = next.state.name,
                    version = next.version,
                    total_minor = cached.total_minor,
                    updated_at = next.updatedAt.toString(),
                )
                queries.insertOutbox(
                    id = mutationId,
                    mutation_type = action.wire,
                    order_id = orderId,
                    payload = SyncJson.encodeToString(
                        OutboxMutation(
                            id = mutationId,
                            action = action.wire,
                            orderId = orderId,
                            restaurantId = cached.restaurant_id,
                            baseVersion = cached.version,
                            actorId = actorId,
                            reasonCode = reasonCode,
                            newEta = newEta?.toString(),
                        )
                    ),
                    created_at = clock().toString(),
                )
                LocalActionResult.Applied(next.version)
            },
            onFailure = { LocalActionResult.Rejected(it.toLocalRejectReason()) },
        )
    }

    // --- outbox replay -------------------------------------------------------

    suspend fun replayOutbox(): ReplayReport {
        val outcomes = mutableListOf<ReplayOutcome>()
        val staleOrders = mutableSetOf<String>()
        for (row in queries.selectOutbox().executeAsList()) {
            val mutation = runCatching { SyncJson.decodeFromString<OutboxMutation>(row.payload) }.getOrNull()
            if (mutation == null) {
                queries.deleteOutbox(row.id)
                continue
            }
            val orderId = mutation.orderId
            val outcome = when {
                orderId in staleOrders -> {
                    queries.deleteOutbox(row.id)
                    ReplayOutcome.Superseded(mutation.id, orderId)
                }
                else -> when (val result = transport.applyMutation(mutation)) {
                    is MutationResult.Applied -> {
                        upsertRemote(result.order)
                        queries.deleteOutbox(row.id)
                        ReplayOutcome.Applied(mutation.id, orderId)
                    }
                    is MutationResult.Stale -> {
                        queries.deleteOutbox(row.id)
                        staleOrders += orderId
                        val remote = transport.fetchOrder(orderId)
                        if (remote != null) upsertRemote(remote) else queries.deleteOrder(orderId)
                        ReplayOutcome.Stale(mutation.id, orderId, result.code)
                    }
                    is MutationResult.Retryable -> {
                        ReplayOutcome.Retryable(mutation.id, orderId, result.message)
                    }
                }
            }
            outcomes += outcome
            // Ordering matters (docs/07-sync-engine.md): stop on transient
            // failure rather than replaying later mutations out of order.
            if (outcome is ReplayOutcome.Retryable) break
        }
        return ReplayReport(outcomes)
    }

    // --- realtime event application -----------------------------------------

    suspend fun applyEvent(event: SyncEvent): EventResult {
        val state = event.state
            ?.let { name -> OrderState.entries.firstOrNull { it.name == name } }
            ?: return EventResult.Ignored
        val orderId = event.aggregateId
        val cached = queries.selectOrder(orderId).executeAsOneOrNull()
        return when {
            cached == null ->
                if (state in KITCHEN_ACTIVE_STATES) recoverOrder(orderId) else EventResult.Ignored
            event.version <= cached.version -> EventResult.Duplicate
            event.version == cached.version + 1 -> {
                if (state.isTerminal) {
                    queries.deleteOrder(orderId)
                } else {
                    queries.upsertOrder(
                        id = orderId,
                        restaurant_id = cached.restaurant_id,
                        state = state.name,
                        version = event.version,
                        total_minor = cached.total_minor,
                        updated_at = event.occurredAt,
                    )
                }
                EventResult.Applied
            }
            else -> recoverOrder(orderId) // version gap -> full resync
        }
    }

    // --- full recovery -------------------------------------------------------

    suspend fun recover(restaurantId: String): RecoverResult {
        // Orders with pending offline mutations are left untouched here; their
        // optimistic state is reconciled by replayOutbox() instead.
        val pendingOrderIds = queries.selectOutbox().executeAsList().map { it.order_id }.toSet()
        val remote = transport.fetchKitchenOrders(restaurantId)
        val remoteIds = remote.map { it.id }.toSet()

        var removed = 0
        for (cached in queries.selectOrders(restaurantId).executeAsList()) {
            if (cached.id in pendingOrderIds) continue
            if (cached.id !in remoteIds) {
                queries.deleteOrder(cached.id)
                removed++
            }
        }

        var replaced = 0
        for (order in remote) {
            if (order.id in pendingOrderIds) continue
            upsertRemote(order)
            replaced++
        }
        return RecoverResult(replaced = replaced, removed = removed)
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun recoverOrder(orderId: String): EventResult {
        val remote = transport.fetchOrder(orderId) ?: return EventResult.Ignored
        upsertRemote(remote)
        return EventResult.Recovered(orderId)
    }

    private fun upsertRemote(remote: RemoteOrder) {
        val state = OrderState.entries.firstOrNull { it.name == remote.state } ?: return
        when {
            state.isTerminal -> queries.deleteOrder(remote.id)
            state in KITCHEN_ACTIVE_STATES -> queries.upsertOrder(
                id = remote.id,
                restaurant_id = remote.restaurantId,
                state = remote.state,
                version = remote.version,
                total_minor = remote.totalMinor,
                updated_at = remote.updatedAt,
            )
            else -> Unit // pre-kitchen state: not part of the kitchen cache
        }
    }

    /** Reconstructs the minimal domain projection the state machine needs. */
    private fun Cached_order.toOrder(): Order = Order(
        id = id,
        restaurantId = restaurant_id,
        total = Money(total_minor),
        createdAt = Instant.parse(updated_at),
        updatedAt = Instant.parse(updated_at),
        state = OrderState.valueOf(state),
        version = version,
    )

    private fun Throwable.toLocalRejectReason(): LocalRejectReason =
        when ((this as? OrderTransitionException)?.failure) {
            TransitionFailure.TERMINAL_STATE -> LocalRejectReason.TERMINAL_STATE
            TransitionFailure.MISSING_REASON_CODE -> LocalRejectReason.MISSING_REASON_CODE
            TransitionFailure.INVALID_ETA -> LocalRejectReason.INVALID_ETA
            TransitionFailure.ILLEGAL_TRANSITION, null -> LocalRejectReason.ILLEGAL_TRANSITION
        }
}
