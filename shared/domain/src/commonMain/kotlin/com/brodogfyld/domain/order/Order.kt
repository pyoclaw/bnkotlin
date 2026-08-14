package com.brodogfyld.domain.order

import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class OrderActorType { CUSTOMER, KITCHEN, ADMIN, SYSTEM, PAYMENT_PROVIDER }

/**
 * Immutable snapshot of a product at the time it was ordered. Once an order is
 * created, item snapshots must not change even if the menu changes later.
 */
@Serializable
data class OrderItem(
    val id: String,
    val productId: String,
    val name: String,
    val unitPrice: Money,
    val quantity: Int,
    val modifierNames: List<String> = emptyList(),
    val lineTotal: Money,
)

/** Audit entry appended on every state transition. */
@Serializable
data class OrderTimelineEvent(
    val eventId: String,
    val occurredAt: Instant,
    val actorType: OrderActorType,
    val actorId: String? = null,
    val previousState: OrderState,
    val newState: OrderState,
    val reasonCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class Order(
    val id: String,
    val restaurantId: String,
    val total: Money,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<OrderItem> = emptyList(),
    val currency: Currency = Currency.DKK,
    val state: OrderState = OrderState.DRAFT,
    val customerId: String? = null,
    val version: Long = 0L,
    val timeline: List<OrderTimelineEvent> = emptyList(),
)
