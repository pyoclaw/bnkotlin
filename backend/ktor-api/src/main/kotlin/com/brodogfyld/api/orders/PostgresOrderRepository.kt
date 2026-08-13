package com.brodogfyld.api.orders

import com.brodogfyld.api.ApiJson
import com.brodogfyld.api.db.Database
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderActorType
import com.brodogfyld.domain.order.OrderItem
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderTimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * PostgreSQL persistence for orders. Canonical source of truth
 * (docs/12-database.md). Optimistic concurrency (the orders.version column) is
 * enforced in the realtime/offline slices (Slice 7/8); for the single-writer
 * milestone the repository does an authoritative full-row sync per save.
 */
class PostgresOrderRepository(private val database: Database) : OrderRepository {

    override suspend fun save(order: Order): Order = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                upsertOrder(conn, order)
                conn.prepareStatement("DELETE FROM order_items WHERE order_id = ?").use { ps ->
                    ps.setString(1, order.id)
                    ps.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM order_timeline_events WHERE order_id = ?").use { ps ->
                    ps.setString(1, order.id)
                    ps.executeUpdate()
                }
                order.items.forEach { insertItem(conn, order.id, it) }
                order.timeline.forEach { insertTimelineEvent(conn, order.id, it) }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        order
    }

    override suspend fun findById(id: String): Order? = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { conn ->
            val row = conn.prepareStatement(
                "SELECT id, restaurant_id, customer_id, currency, total_minor, state, version, created_at, updated_at " +
                    "FROM orders WHERE id = ?"
            ).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toOrderRow() else null }
            } ?: return@withContext null
            row.copy(items = loadItems(conn, id), timeline = loadTimeline(conn, id))
        }
    }

    override suspend fun findByRestaurant(restaurantId: String): List<Order> = withContext(Dispatchers.IO) {
        queryOrders(
            "SELECT id, restaurant_id, customer_id, currency, total_minor, state, version, created_at, updated_at " +
                "FROM orders WHERE restaurant_id = ? ORDER BY created_at DESC",
            { it.setString(1, restaurantId) },
        )
    }

    override suspend fun findByRestaurantAndStates(restaurantId: String, states: Set<OrderState>): List<Order> =
        withContext(Dispatchers.IO) {
            queryOrders(
                "SELECT id, restaurant_id, customer_id, currency, total_minor, state, version, created_at, updated_at " +
                    "FROM orders WHERE restaurant_id = ? AND state = ANY(?) ORDER BY created_at DESC",
                { ps ->
                    ps.setString(1, restaurantId)
                    ps.setArray(2, ps.connection.createArrayOf("text", states.map { it.name }.toTypedArray()))
                },
            )
        }

    private fun queryOrders(sql: String, bind: (java.sql.PreparedStatement) -> Unit): List<Order> {
        database.dataSource.connection.use { conn ->
            val ids = mutableListOf<String>()
            val rows = mutableListOf<Order>()
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = rs.toOrderRow()
                        ids += row.id
                        rows += row
                    }
                }
            }
            return rows.map { row -> row.copy(items = loadItems(conn, row.id), timeline = loadTimeline(conn, row.id)) }
        }
    }

    private fun upsertOrder(conn: Connection, order: Order) {
        conn.prepareStatement(
            "INSERT INTO orders (id, restaurant_id, customer_id, currency, total_minor, state, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "customer_id = EXCLUDED.customer_id, currency = EXCLUDED.currency, total_minor = EXCLUDED.total_minor, " +
                "state = EXCLUDED.state, version = EXCLUDED.version, updated_at = EXCLUDED.updated_at"
        ).use { ps ->
            ps.setString(1, order.id)
            ps.setString(2, order.restaurantId)
            ps.setString(3, order.customerId)
            ps.setString(4, order.currency.code)
            ps.setLong(5, order.total.amountMinor)
            ps.setString(6, order.state.name)
            ps.setLong(7, order.version)
            ps.setObject(8, order.createdAt.toJavaInstant().atOffset(ZoneOffset.UTC))
            ps.setObject(9, order.updatedAt.toJavaInstant().atOffset(ZoneOffset.UTC))
            ps.executeUpdate()
        }
    }

    private fun insertItem(conn: Connection, orderId: String, item: OrderItem) {
        conn.prepareStatement(
            "INSERT INTO order_items (id, order_id, product_id, name, unit_price_minor, quantity, line_total_minor, modifier_names) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))"
        ).use { ps ->
            ps.setString(1, item.id)
            ps.setString(2, orderId)
            ps.setString(3, item.productId)
            ps.setString(4, item.name)
            ps.setLong(5, item.unitPrice.amountMinor)
            ps.setInt(6, item.quantity)
            ps.setLong(7, item.lineTotal.amountMinor)
            ps.setString(8, ApiJson.encodeToString(item.modifierNames))
            ps.executeUpdate()
        }
    }

    private fun insertTimelineEvent(conn: Connection, orderId: String, event: OrderTimelineEvent) {
        conn.prepareStatement(
            "INSERT INTO order_timeline_events " +
                "(order_id, event_id, occurred_at, actor_type, actor_id, previous_state, new_state, reason_code, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))"
        ).use { ps ->
            ps.setString(1, orderId)
            ps.setString(2, event.eventId)
            ps.setObject(3, event.occurredAt.toJavaInstant().atOffset(ZoneOffset.UTC))
            ps.setString(4, event.actorType.name)
            ps.setString(5, event.actorId)
            ps.setString(6, event.previousState.name)
            ps.setString(7, event.newState.name)
            ps.setString(8, event.reasonCode)
            ps.setString(9, ApiJson.encodeToString(event.metadata))
            ps.executeUpdate()
        }
    }

    private fun loadItems(conn: Connection, orderId: String): List<OrderItem> {
        return conn.prepareStatement(
            "SELECT id, product_id, name, unit_price_minor, quantity, line_total_minor, modifier_names " +
                "FROM order_items WHERE order_id = ? ORDER BY id"
        ).use { ps ->
            ps.setString(1, orderId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            OrderItem(
                                id = rs.getString("id"),
                                productId = rs.getString("product_id"),
                                name = rs.getString("name"),
                                unitPrice = Money(rs.getLong("unit_price_minor")),
                                quantity = rs.getInt("quantity"),
                                modifierNames = ApiJson.decodeFromString(rs.getString("modifier_names")),
                                lineTotal = Money(rs.getLong("line_total_minor")),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadTimeline(conn: Connection, orderId: String): List<OrderTimelineEvent> {
        return conn.prepareStatement(
            "SELECT event_id, occurred_at, actor_type, actor_id, previous_state, new_state, reason_code, metadata " +
                "FROM order_timeline_events WHERE order_id = ? ORDER BY id"
        ).use { ps ->
            ps.setString(1, orderId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            OrderTimelineEvent(
                                eventId = rs.getString("event_id"),
                                occurredAt = rs.getObject("occurred_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
                                actorType = OrderActorType.valueOf(rs.getString("actor_type")),
                                actorId = rs.getString("actor_id"),
                                previousState = OrderState.valueOf(rs.getString("previous_state")),
                                newState = OrderState.valueOf(rs.getString("new_state")),
                                reasonCode = rs.getString("reason_code"),
                                metadata = ApiJson.decodeFromString(rs.getString("metadata")),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun java.sql.ResultSet.toOrderRow(): Order = Order(
        id = getString("id"),
        restaurantId = getString("restaurant_id"),
        total = Money(getLong("total_minor")),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
        currency = Currency.entries.firstOrNull { it.code == getString("currency") } ?: Currency.DKK,
        state = OrderState.valueOf(getString("state")),
        customerId = getString("customer_id"),
        version = getLong("version"),
    )
}
