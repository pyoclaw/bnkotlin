package com.brodogfyld.api.db

import com.brodogfyld.api.ApiJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Exposed DSL mappings for the Flyway-managed PostgreSQL schema
 * (database/migrations). Flyway owns schema creation and evolution; these
 * objects only describe columns for type-safe reads and writes.
 */
object OrdersTable : Table("orders") {
    val id = varchar("id", 64)
    val restaurantId = varchar("restaurant_id", 64)
    val customerId = varchar("customer_id", 64).nullable()
    val currency = varchar("currency", 8)
    val totalMinor = long("total_minor")
    val state = varchar("state", 32)
    val version = long("version")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object OrderItemsTable : Table("order_items") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val productId = varchar("product_id", 64)
    val name = varchar("name", 256)
    val unitPriceMinor = long("unit_price_minor")
    val quantity = integer("quantity")
    val lineTotalMinor = long("line_total_minor")
    val modifierNames = jsonb<List<String>>(
        "modifier_names",
        serialize = { ApiJson.encodeToString(it) },
        deserialize = { ApiJson.decodeFromString(it) },
    )

    override val primaryKey = PrimaryKey(id)
}

object OrderTimelineEventsTable : Table("order_timeline_events") {
    val id = long("id").autoIncrement()
    val orderId = varchar("order_id", 64).index()
    val eventId = varchar("event_id", 64).uniqueIndex()
    val occurredAt = timestampWithTimeZone("occurred_at")
    val actorType = varchar("actor_type", 32)
    val actorId = varchar("actor_id", 64).nullable()
    val previousState = varchar("previous_state", 32)
    val newState = varchar("new_state", 32)
    val reasonCode = varchar("reason_code", 128).nullable()
    val metadata = jsonb<Map<String, String>>(
        "metadata",
        serialize = { ApiJson.encodeToString(it) },
        deserialize = { ApiJson.decodeFromString(it) },
    )

    override val primaryKey = PrimaryKey(id)
}

object OutboxEventsTable : Table("outbox_events") {
    val id = long("id").autoIncrement()
    val eventId = varchar("event_id", 64).uniqueIndex()
    val aggregateType = varchar("aggregate_type", 64)
    val aggregateId = varchar("aggregate_id", 64).index()
    val eventType = varchar("event_type", 64)
    val aggregateVersion = long("aggregate_version")
    val payload = jsonb<JsonElement>(
        "payload",
        serialize = { it.toString() },
        deserialize = { ApiJson.parseToJsonElement(it) },
    )
    val createdAt = timestampWithTimeZone("created_at")
    val publishedAt = timestampWithTimeZone("published_at").nullable().index()
    val attempts = integer("attempts").default(0)

    override val primaryKey = PrimaryKey(id)
}
