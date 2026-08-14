package com.brodogfyld.sync

import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderStateMachine
import kotlinx.datetime.Instant

/**
 * In-memory [SyncTransport] that mirrors the backend: it applies mutations
 * through the shared [OrderStateMachine] and deduplicates retries by mutation
 * id, so the sync engine can be tested against a faithful server double
 * without a network.
 */
class FakeSyncTransport : SyncTransport {

    data class ServerOrder(
        val id: String,
        val restaurantId: String,
        var state: OrderState,
        var version: Long,
        val totalMinor: Long,
        var updatedAt: String,
    )

    private val orders = mutableMapOf<String, ServerOrder>()
    private val appliedMutationIds = mutableSetOf<String>()

    /** When set, mutations with this action return a retryable failure. */
    var failOnAction: String? = null

    fun seed(order: RemoteOrder) {
        orders[order.id] = ServerOrder(
            id = order.id,
            restaurantId = order.restaurantId,
            state = OrderState.valueOf(order.state),
            version = order.version,
            totalMinor = order.totalMinor,
            updatedAt = order.updatedAt,
        )
    }

    /** Simulates a mutation the server already applied before the client crashed. */
    fun markApplied(mutationId: String) {
        appliedMutationIds += mutationId
    }

    fun serverOrder(id: String): RemoteOrder? = orders[id]?.toRemote()

    override suspend fun applyMutation(mutation: OutboxMutation): MutationResult {
        if (mutation.action == failOnAction) {
            return MutationResult.Retryable("network down")
        }
        val server = orders[mutation.orderId] ?: return MutationResult.Stale("order_not_found")
        if (mutation.id in appliedMutationIds) {
            return MutationResult.Applied(server.toRemote())
        }
        val occurredAt = Instant.parse(server.updatedAt)
        val current = Order(
            id = server.id,
            restaurantId = server.restaurantId,
            total = Money(server.totalMinor),
            createdAt = occurredAt,
            updatedAt = occurredAt,
            state = server.state,
            version = server.version,
        )
        val transition = when (mutation.action) {
            "accept" -> OrderStateMachine.accept(current, mutation.actorId ?: "unknown", mutation.id, occurredAt)
            "reject" -> OrderStateMachine.reject(current, mutation.actorId ?: "unknown", mutation.reasonCode ?: "", mutation.id, occurredAt)
            "delay" -> {
                val eta = mutation.newEta?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: return MutationResult.Stale("invalid_eta")
                OrderStateMachine.delay(current, mutation.actorId ?: "unknown", eta, mutation.id, occurredAt)
            }
            "ready" -> OrderStateMachine.ready(current, mutation.actorId ?: "unknown", mutation.id, occurredAt)
            "complete" -> OrderStateMachine.complete(current, mutation.actorId ?: "unknown", mutation.id, occurredAt)
            else -> return MutationResult.Stale("unknown_action")
        }
        return transition.fold(
            onSuccess = { next ->
                appliedMutationIds += mutation.id
                server.state = next.state
                server.version = next.version
                server.updatedAt = next.updatedAt.toString()
                MutationResult.Applied(server.toRemote())
            },
            onFailure = { MutationResult.Stale("invalid_transition") },
        )
    }

    override suspend fun fetchOrder(orderId: String): RemoteOrder? = orders[orderId]?.toRemote()

    override suspend fun fetchKitchenOrders(restaurantId: String): List<RemoteOrder> =
        orders.values
            .filter { it.restaurantId == restaurantId && it.state in KITCHEN_ACTIVE_STATES }
            .map { it.toRemote() }

    private fun ServerOrder.toRemote() = RemoteOrder(
        id = id,
        restaurantId = restaurantId,
        state = state.name,
        totalMinor = totalMinor,
        version = version,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
