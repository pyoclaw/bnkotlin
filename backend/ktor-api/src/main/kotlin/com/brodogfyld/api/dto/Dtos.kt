package com.brodogfyld.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class HealthStatus(val status: String, val ready: Boolean = false)

@Serializable
data class RestaurantStatusDto(
    val id: String,
    val name: String,
    val open: Boolean,
    val acceptsOrders: Boolean,
    val currency: String,
    val timezone: String,
)

/**
 * Realtime event envelope. Mirrors the contract in docs/19-realtime-protocol.md:
 * eventId, type, aggregateId, version, occurredAt and a typed payload.
 */
@Serializable
data class EventEnvelope(
    val eventId: String,
    val type: String,
    val aggregateId: String,
    val version: Long,
    val occurredAt: String,
    val payload: JsonElement = JsonNull,
)
