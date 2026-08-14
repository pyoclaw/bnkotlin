package com.brodogfyld.sync

import com.brodogfyld.domain.order.OrderState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Codec for outbox payloads and realtime event envelopes. */
val SyncJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Wire names for kitchen mutations, matching the backend endpoints. */
enum class KitchenAction(val wire: String) {
    ACCEPT("accept"),
    REJECT("reject"),
    DELAY("delay"),
    READY("ready"),
    COMPLETE("complete");

    companion object {
        fun fromWire(wire: String): KitchenAction? = entries.firstOrNull { it.wire == wire }
    }
}

/** Order states the kitchen queue renders (mirrors OrderService.kitchenOrders). */
val KITCHEN_ACTIVE_STATES: Set<OrderState> = setOf(
    OrderState.QUEUED,
    OrderState.ACCEPTED,
    OrderState.PREPARING,
    OrderState.DELAYED,
    OrderState.READY,
)

/**
 * Authoritative order projection returned by the server (single order GET,
 * kitchen list, and mutation responses). The kitchen cache stores only the
 * fields it renders; this is the superset the sync engine reconciles from.
 */
@Serializable
data class RemoteOrder(
    val id: String,
    val restaurantId: String,
    val state: String,
    val currency: String = "DKK",
    val totalMinor: Long,
    val version: Long,
    val customerId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * A pending offline kitchen mutation persisted in `local_outbox`. [id] is the
 * client-generated idempotency key and doubles as the server timeline event id,
 * so retries are recognized and deduplicated (docs/07-sync-engine.md). [baseVersion]
 * records the cached version the optimistic apply was computed against.
 */
@Serializable
data class OutboxMutation(
    val id: String,
    val action: String,
    val orderId: String,
    val restaurantId: String,
    val baseVersion: Long,
    val actorId: String? = null,
    val reasonCode: String? = null,
    val newEta: String? = null,
)

/**
 * Realtime event envelope received over the WebSocket
 * (docs/19-realtime-protocol.md). Unknown event types and states are ignored so
 * the protocol stays forward-compatible.
 */
@Serializable
data class SyncEvent(
    val eventId: String,
    val type: String,
    val aggregateId: String,
    val version: Long,
    val occurredAt: String,
    val payload: JsonElement = JsonNull,
) {
    /** The `state` field carried by order events, if present and a string. */
    val state: String?
        get() = (payload as? JsonObject)
            ?.get("state")
            ?.let { it as? JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content
}
