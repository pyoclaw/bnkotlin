package com.brodogfyld.api.dto

import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderTimelineEvent
import kotlinx.serialization.Serializable

@Serializable
data class OrderItemRequest(
    val productId: String,
    val quantity: Int,
    val modifierOptionIds: Set<String> = emptySet(),
)

@Serializable
data class CreateOrderRequest(
    val restaurantId: String,
    val currency: String = "DKK",
    val customerId: String? = null,
    val items: List<OrderItemRequest> = emptyList(),
)

@Serializable
data class OrderItemResponse(
    val id: String,
    val productId: String,
    val name: String,
    val unitPriceMinor: Long,
    val quantity: Int,
    val lineTotalMinor: Long,
    val modifierNames: List<String>,
)

@Serializable
data class TimelineEventResponse(
    val eventId: String,
    val occurredAt: String,
    val actorType: String,
    val actorId: String? = null,
    val previousState: String,
    val newState: String,
    val reasonCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class OrderResponse(
    val id: String,
    val restaurantId: String,
    val state: String,
    val currency: String,
    val totalMinor: Long,
    val version: Long,
    val customerId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val items: List<OrderItemResponse> = emptyList(),
)

@Serializable
data class KitchenActionRequest(
    val actorId: String? = null,
    val reasonCode: String? = null,
    val newEta: String? = null,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val details: List<String> = emptyList(),
)

fun Order.toResponse(): OrderResponse = OrderResponse(
    id = id,
    restaurantId = restaurantId,
    state = state.name,
    currency = currency.code,
    totalMinor = total.amountMinor,
    version = version,
    customerId = customerId,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    items = items.map { item ->
        OrderItemResponse(
            id = item.id,
            productId = item.productId,
            name = item.name,
            unitPriceMinor = item.unitPrice.amountMinor,
            quantity = item.quantity,
            lineTotalMinor = item.lineTotal.amountMinor,
            modifierNames = item.modifierNames,
        )
    },
)

fun OrderTimelineEvent.toResponse(): TimelineEventResponse = TimelineEventResponse(
    eventId = eventId,
    occurredAt = occurredAt.toString(),
    actorType = actorType.name,
    actorId = actorId,
    previousState = previousState.name,
    newState = newState.name,
    reasonCode = reasonCode,
    metadata = metadata,
)
