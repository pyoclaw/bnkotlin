package com.brodogfyld.api.realtime

import com.brodogfyld.api.dto.EventEnvelope
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState
import kotlinx.coroutines.channels.SendChannel
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A single order event emitted after a successful, persisted state change.
 * Mirrors the contract in docs/19-realtime-protocol.md: eventId, type,
 * aggregateId, version, occurredAt and a typed payload. [eventId] matches the
 * corresponding timeline event id so the realtime stream and the audit trail
 * stay consistent.
 */
data class OrderEvent(
    val eventId: String,
    val type: String,
    val orderId: String,
    val restaurantId: String,
    val version: Long,
    val occurredAt: Instant,
    val state: OrderState,
) {
    fun toEnvelope(): EventEnvelope = EventEnvelope(
        eventId = eventId,
        type = type,
        aggregateId = orderId,
        version = version,
        occurredAt = occurredAt.toString(),
        payload = buildJsonObject {
            put("state", state.name)
            put("restaurantId", restaurantId)
        },
    )
}

/**
 * Maps an order state to its realtime event type (docs/07-sync-engine.md),
 * one event per transition. Unknown types are ignored by clients, which keeps
 * the protocol forward-compatible.
 */
fun OrderState.eventType(): String = when (this) {
    OrderState.DRAFT -> "order.created"
    OrderState.PENDING_PAYMENT -> "order.submitted"
    OrderState.PAYMENT_AUTHORIZED -> "order.payment_authorized"
    OrderState.PAID -> "order.paid"
    OrderState.QUEUED -> "order.queued"
    OrderState.ACCEPTED -> "order.accepted"
    OrderState.PREPARING -> "order.preparing"
    OrderState.DELAYED -> "order.delayed"
    OrderState.READY -> "order.ready"
    OrderState.COMPLETED -> "order.completed"
    OrderState.REJECTED -> "order.rejected"
    OrderState.CANCELLED -> "order.cancelled"
    OrderState.REFUNDED -> "order.refunded"
    OrderState.EXPIRED -> "order.expired"
}

/** Builds the realtime event for the most recent transition on [this] order. */
fun Order.toOrderEvent(): OrderEvent {
    val last = timeline.last()
    return OrderEvent(
        eventId = last.eventId,
        type = last.newState.eventType(),
        orderId = id,
        restaurantId = restaurantId,
        version = version,
        occurredAt = last.occurredAt,
        state = last.newState,
    )
}

/**
 * In-memory fan-out for order events. Each subscriber owns a bounded channel,
 * so a slow or stalled client cannot block an HTTP request handler; it simply
 * drops events and recovers over HTTP (docs/07-sync-engine.md "HTTP for
 * initial load and recovery"). Single-node only: a durable event log/broker is
 * deferred until multi-instance deployments (Slice 8/10).
 */
class OrderEventBus {
    private val subscribers = CopyOnWriteArrayList<SendChannel<OrderEvent>>()

    fun subscribe(channel: SendChannel<OrderEvent>) {
        subscribers += channel
    }

    fun unsubscribe(channel: SendChannel<OrderEvent>) {
        subscribers -= channel
    }

    /** Publishes an event to live subscribers; drops it when no one listens. */
    fun publish(event: OrderEvent) {
        for (channel in subscribers) {
            channel.trySend(event)
        }
    }
}
