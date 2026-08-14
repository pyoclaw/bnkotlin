package com.brodogfyld.api.orders

import com.brodogfyld.api.ApiJson
import com.brodogfyld.api.db.Database
import com.brodogfyld.api.db.OrderItemsTable
import com.brodogfyld.api.db.OrderTimelineEventsTable
import com.brodogfyld.api.db.OrdersTable
import com.brodogfyld.api.db.OutboxEventsTable
import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderActorType
import com.brodogfyld.domain.order.OrderItem
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderTimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant as KInstant
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant as JInstant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * PostgreSQL persistence for orders via JetBrains Exposed
 * (Ktor -> service -> OrderRepository -> Exposed -> PostgreSQL).
 *
 * Concurrency: [update] applies an optimistic, version-checked write inside a
 * single transaction; a stale write raises [OrderConcurrencyException] and the
 * whole transaction rolls back. The order row, item snapshots, audit timeline
 * and outbox rows commit atomically, so realtime events can never represent
 * uncommitted state (docs/12-database.md, docs/07-sync-engine.md).
 */
class PostgresOrderRepository(private val database: Database) : OrderRepository {

    override suspend fun create(order: Order, events: List<OrderEvent>): Order = withContext(Dispatchers.IO) {
        transaction(database.exposed) {
            insertOrder(order)
            order.items.forEach { insertItem(order.id, it) }
            order.timeline.forEach { insertTimelineEvent(order.id, it) }
            events.forEach { insertOutboxEvent(it) }
        }
        order
    }

    override suspend fun update(order: Order, expectedVersion: Long, events: List<OrderEvent>): Order =
        withContext(Dispatchers.IO) {
            transaction(database.exposed) {
                val updated = OrdersTable.update({
                    (OrdersTable.id eq order.id) and (OrdersTable.version eq expectedVersion)
                }) { row ->
                    row[customerId] = order.customerId
                    row[currency] = order.currency.code
                    row[totalMinor] = order.total.amountMinor
                    row[state] = order.state.name
                    row[version] = order.version
                    row[updatedAt] = order.updatedAt.toOffsetDateTime()
                }
                if (updated == 0) {
                    val current = OrdersTable.selectAll()
                        .where { OrdersTable.id eq order.id }
                        .singleOrNull()
                    val actual = current?.get(OrdersTable.version)
                        ?: throw OrderNotFoundException(order.id)
                    throw OrderConcurrencyException(order.id, expectedVersion, actual)
                }
                replaceChildren(order)
                events.forEach { insertOutboxEvent(it) }
            }
            order
        }

    override suspend fun findById(id: String): Order? = withContext(Dispatchers.IO) {
        transaction(database.exposed) {
            val row = OrdersTable.selectAll().where { OrdersTable.id eq id }.singleOrNull()
                ?: return@transaction null
            row.toOrder().copy(items = loadItems(id), timeline = loadTimeline(id))
        }
    }

    override suspend fun findByRestaurant(restaurantId: String): List<Order> = withContext(Dispatchers.IO) {
        transaction(database.exposed) {
            OrdersTable.selectAll()
                .where { OrdersTable.restaurantId eq restaurantId }
                .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                .map { it.toOrder().withChildren() }
        }
    }

    override suspend fun findByRestaurantAndStates(restaurantId: String, states: Set<OrderState>): List<Order> =
        withContext(Dispatchers.IO) {
            transaction(database.exposed) {
                OrdersTable.selectAll()
                    .where {
                        (OrdersTable.restaurantId eq restaurantId) and
                            (OrdersTable.state inList states.map { it.name })
                    }
                    .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .map { it.toOrder().withChildren() }
            }
        }

    // --- writes -----------------------------------------------------------------

    private fun insertOrder(order: Order) {
        OrdersTable.insert { row ->
            row[id] = order.id
            row[restaurantId] = order.restaurantId
            row[customerId] = order.customerId
            row[currency] = order.currency.code
            row[totalMinor] = order.total.amountMinor
            row[state] = order.state.name
            row[version] = order.version
            row[createdAt] = order.createdAt.toOffsetDateTime()
            row[updatedAt] = order.updatedAt.toOffsetDateTime()
        }
    }

    /** Deletes and re-inserts child rows (idempotent full-row sync). */
    private fun replaceChildren(order: Order) {
        OrderItemsTable.deleteWhere { OrderItemsTable.orderId eq order.id }
        OrderTimelineEventsTable.deleteWhere { OrderTimelineEventsTable.orderId eq order.id }
        order.items.forEach { insertItem(order.id, it) }
        order.timeline.forEach { insertTimelineEvent(order.id, it) }
    }

    private fun insertItem(orderId: String, item: OrderItem) {
        OrderItemsTable.insert { row ->
            row[id] = item.id
            row[OrderItemsTable.orderId] = orderId
            row[productId] = item.productId
            row[name] = item.name
            row[unitPriceMinor] = item.unitPrice.amountMinor
            row[quantity] = item.quantity
            row[lineTotalMinor] = item.lineTotal.amountMinor
            row[modifierNames] = item.modifierNames
        }
    }

    private fun insertTimelineEvent(orderId: String, event: OrderTimelineEvent) {
        OrderTimelineEventsTable.insert { row ->
            row[OrderTimelineEventsTable.orderId] = orderId
            row[eventId] = event.eventId
            row[occurredAt] = event.occurredAt.toOffsetDateTime()
            row[actorType] = event.actorType.name
            row[actorId] = event.actorId
            row[previousState] = event.previousState.name
            row[newState] = event.newState.name
            row[reasonCode] = event.reasonCode
            row[metadata] = event.metadata
        }
    }

    private fun insertOutboxEvent(event: OrderEvent) {
        OutboxEventsTable.insert { row ->
            row[eventId] = event.eventId
            row[aggregateType] = "order"
            row[aggregateId] = event.orderId
            row[eventType] = event.type
            row[aggregateVersion] = event.version
            row[payload] = ApiJson.encodeToJsonElement(event.toEnvelope())
            row[createdAt] = event.occurredAt.toOffsetDateTime()
        }
    }

    // --- reads -----------------------------------------------------------------

    private fun ResultRow.toOrder(): Order = Order(
        id = get(OrdersTable.id),
        restaurantId = get(OrdersTable.restaurantId),
        total = Money(get(OrdersTable.totalMinor)),
        createdAt = get(OrdersTable.createdAt).toKInstant(),
        updatedAt = get(OrdersTable.updatedAt).toKInstant(),
        currency = Currency.entries.firstOrNull { it.code == get(OrdersTable.currency) } ?: Currency.DKK,
        state = OrderState.valueOf(get(OrdersTable.state)),
        customerId = get(OrdersTable.customerId),
        version = get(OrdersTable.version),
    )

    private fun Order.withChildren(): Order = copy(items = loadItems(id), timeline = loadTimeline(id))

    private fun loadItems(orderId: String): List<OrderItem> =
        OrderItemsTable.selectAll()
            .where { OrderItemsTable.orderId eq orderId }
            .orderBy(OrderItemsTable.id)
            .map { row ->
                OrderItem(
                    id = row[OrderItemsTable.id],
                    productId = row[OrderItemsTable.productId],
                    name = row[OrderItemsTable.name],
                    unitPrice = Money(row[OrderItemsTable.unitPriceMinor]),
                    quantity = row[OrderItemsTable.quantity],
                    modifierNames = row[OrderItemsTable.modifierNames],
                    lineTotal = Money(row[OrderItemsTable.lineTotalMinor]),
                )
            }

    private fun loadTimeline(orderId: String): List<OrderTimelineEvent> =
        OrderTimelineEventsTable.selectAll()
            .where { OrderTimelineEventsTable.orderId eq orderId }
            .orderBy(OrderTimelineEventsTable.id)
            .map { row ->
                OrderTimelineEvent(
                    eventId = row[OrderTimelineEventsTable.eventId],
                    occurredAt = row[OrderTimelineEventsTable.occurredAt].toKInstant(),
                    actorType = OrderActorType.valueOf(row[OrderTimelineEventsTable.actorType]),
                    actorId = row[OrderTimelineEventsTable.actorId],
                    previousState = OrderState.valueOf(row[OrderTimelineEventsTable.previousState]),
                    newState = OrderState.valueOf(row[OrderTimelineEventsTable.newState]),
                    reasonCode = row[OrderTimelineEventsTable.reasonCode],
                    metadata = row[OrderTimelineEventsTable.metadata],
                )
            }

    // kotlinx-datetime 0.6.2 <-> java.time, without relying on the internal
    // toJavaInstant/toKotlinInstant helpers (which Exposed's transitive
    // kotlinx-datetime compat artifact marks internal).
    private fun KInstant.toOffsetDateTime(): OffsetDateTime =
        JInstant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()).atOffset(ZoneOffset.UTC)

    private fun OffsetDateTime.toKInstant(): KInstant =
        KInstant.fromEpochSeconds(toEpochSecond(), nano)
}
